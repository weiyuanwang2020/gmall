package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ActivityController {

    /**
     * 秒杀列表
     * @return
     */
    @GetMapping("couponInfo.html")
    public String index(){
        return "couponInfo/index";
    }

}
