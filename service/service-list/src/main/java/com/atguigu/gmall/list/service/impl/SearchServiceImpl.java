package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();

        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
        if(attrList != null){
            List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
                return searchAttr;
            }).collect(Collectors.toList());
            goods.setAttrs(searchAttrList);
        }

        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
        if(trademark != null){
            goods.setTmId(skuInfo.getTmId());
            goods.setTmName(trademark.getTmName());
            goods.setTmLogoUrl(trademark.getLogoUrl());
        }

        BaseCategoryView baseCategoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        if(baseCategoryView != null){
            goods.setCategory1Id(baseCategoryView.getCategory1Id());
            goods.setCategory1Name(baseCategoryView.getCategory1Name());
            goods.setCategory2Id(baseCategoryView.getCategory2Id());
            goods.setCategory2Name(baseCategoryView.getCategory2Name());
            goods.setCategory3Id(baseCategoryView.getCategory3Id());
            goods.setCategory3Name(baseCategoryView.getCategory3Name());
        }

        goods.setDefaultImg(skuInfo.getSkuDefaultImg());
        goods.setPrice(skuInfo.getPrice().doubleValue());
        goods.setId(skuInfo.getId());
        goods.setTitle(skuInfo.getSkuName());
        goods.setCreateTime(new Date());

        goodsRepository.save(goods);
    }

    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        String hotKey = "hotScore";

        Double hotScore = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);

        if(hotScore % 10 == 0){
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(Math.round(hotScore));
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        SearchRequest searchRequest = buildQueryDsl(searchParam);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        SearchResponseVo responseVo = parseSearchResult(response);
        responseVo.setPageNo(searchParam.getPageNo());
        responseVo.setPageSize(searchParam.getPageSize());
        Long totalPages = (responseVo.getTotal() + searchParam.getPageSize() - 1) / searchParam.getPageSize();
        responseVo.setTotalPages(totalPages);
        return responseVo;
    }


    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if(!StringUtils.isEmpty(searchParam.getKeyword())){
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }
        String trademark = searchParam.getTrademark();
        if(!StringUtils.isEmpty(trademark)){
            String[] split = trademark.split(":");
            if(split != null && split.length == 2){
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId", split[0]));
            }
        }
        if(searchParam.getCategory1Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        // 构建分类过滤
        if(null!=searchParam.getCategory2Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }
        // 构建分类过滤
        if(null!=searchParam.getCategory3Id()){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }

        String[] props = searchParam.getProps();
        if(props != null && props.length > 0){
            for (String prop : props) {
                String[] split = prop.split(":");
                if(split != null && split.length == 3){
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue", split[1]));
                    boolQuery.must(QueryBuilders.nestedQuery("attrs", subBoolQuery, ScoreMode.None));
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }

        searchSourceBuilder.query(boolQueryBuilder);
        int from = (searchParam.getPageNo() - 1) * searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        String order = searchParam.getOrder();
        if(!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            if(split != null && split.length == 2){
                String field = null;
                switch (split[0]){
                    case "1" :
                        field = "hotScore";
                        break;
                    case "2" :
                        field = "price";
                        break;
                }
                searchSourceBuilder.sort(field, "asc".equals(split[1]) ? SortOrder.ASC : SortOrder.DESC);
            }else{
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }

        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title").postTags("</span>").preTags("<span style=color:red>");
        searchSourceBuilder.highlighter(highlightBuilder);

        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(termsAggregationBuilder);

        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"}, null);

        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);

        System.out.println("dsl:" + searchSourceBuilder.toString());

        return searchRequest;
    }


    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        SearchHits hits = response.getHits();
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();

        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo trademark = new SearchResponseTmVo();
            trademark.setTmId(Long.parseLong(((Terms.Bucket) bucket).getKeyAsString()));
            Map<String, Aggregation> tmIdSubMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            trademark.setTmName(tmName);
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdSubMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            trademark.setTmLogoUrl(tmLogoUrl);
            return trademark;
        }).collect(Collectors.toList());
        searchResponseVo.setTrademarkList(trademarkList);

        SearchHit[] subHits = hits.getHits();
        List<Goods> goodsList = new ArrayList<>();
        if(subHits != null && subHits.length > 0){
            for (SearchHit subHit : subHits) {
                Goods goods = JSONObject.parseObject(subHit.getSourceAsString(), Goods.class);
                if(subHit.getHighlightFields().get("title") != null){
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);

        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if(!CollectionUtils.isEmpty(buckets)){
            List<SearchResponseAttrVo> searchResponseAttrVoList = buckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                searchResponseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                List<? extends Terms.Bucket> nameBuckets = attrNameAgg.getBuckets();
                searchResponseAttrVo.setAttrName(nameBuckets.get(0).getKeyAsString());
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueBuckets = attrValueAgg.getBuckets();
                List<String> values = attrValueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(values);
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(searchResponseAttrVoList);
        }

        searchResponseVo.setTotal(hits.totalHits);

        return searchResponseVo;
    }


}
