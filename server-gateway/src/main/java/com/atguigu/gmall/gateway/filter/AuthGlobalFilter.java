package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${authUrls.url}")
    private String authUrls;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if(antPathMatcher.match("/**/inner/**", path)){
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        String userId = getUserId(request);
        if("-1".equals(userId)){
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        if(antPathMatcher.match("/api/**/auth/**", path)){
            if(StringUtils.isEmpty(userId)){
                ServerHttpResponse response = exchange.getResponse();
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        String[] split = authUrls.split(",");
        for (String url : split) {
            if(path.indexOf(url) != -1 && StringUtils.isEmpty(userId)){
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://www.gmall.com/login.html?originUrl="+request.getURI());
                return response.setComplete();
            }
        }

        String userTempId = getUserTempId(request);
        if(!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            if (!StringUtils.isEmpty(userId)) {
                request.mutate().header("userId", userId).build();
            }
            if(!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId", userTempId).build();
            }
            //return chain.filter(exchange);
            return chain.filter(exchange.mutate().request(request).build());
        }

        return chain.filter(exchange);
    }

    /**
     * 获取当前登录用户id
     * @param request
     * @return
     */
    private String getUserId(ServerHttpRequest request) {
        String token = "";
        List<String> tokenList = request.getHeaders().get("token");
        if(tokenList != null){
            token = tokenList.get(0);
        }else{
            HttpCookie cookie = request.getCookies().getFirst("token");
            if(cookie != null){
                token = URLDecoder.decode(cookie.getValue());
            }
        }
        if(!StringUtils.isEmpty(token)){
            String userJsonString = (String) redisTemplate.opsForValue().get("user:login:" + token);
            JSONObject jsonObject = JSONObject.parseObject(userJsonString);
            String ip = jsonObject.getString("ip");
            if(ip.equals(IpUtil.getGatwayIpAddress(request))){
                return jsonObject.getString("userId");
            }else{
                return "-1";
            }
        }
        return null;
    }

    /**
     * 获取当前用户临时用户id
     * @param request
     * @return
     */
    public String getUserTempId(ServerHttpRequest request){
        String userTempId = "";
        List<String> headerTempIdList = request.getHeaders().get("userTempId");
        if(headerTempIdList != null){
            userTempId = headerTempIdList.get(0);
        }else{
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if(cookie != null){
                userTempId = URLDecoder.decode(cookie.getValue());
            }
        }
        return userTempId;
    }


    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        Result<Object> result = Result.build(null, resultCodeEnum);
        byte[] bytes = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(wrap));
    }

}
