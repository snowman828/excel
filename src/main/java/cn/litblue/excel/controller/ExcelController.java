package cn.litblue.excel.controller;

import cn.litblue.excel.service.ExcelService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * @author: litblue
 * @since: 2019/12/23 16:53
 */

@RestController
@RequestMapping("excel")
public class ExcelController {

    @Resource
    private ExcelService excelService;


    @PostMapping("import")
    public String importExcelByEasyExcel(@RequestParam(value = "file", required = false)MultipartFile file){

        long startTime = System.currentTimeMillis();

        if (file == null){
            return "请先上传文件";
        }

        // 调用 service 方法，返回导入是否成功的结果
        boolean flag = excelService.importExcelByEasyExcel(file);

        long endTime = System.currentTimeMillis();

        long costTime = endTime - startTime;

        return flag ? "导入成功，耗费时间--->" + costTime : "导入失败，耗费时间--->" + costTime;

    }
}
