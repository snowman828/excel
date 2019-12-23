package cn.litblue.excel.service;

import cn.litblue.excel.entity.Excel;
import cn.litblue.excel.mapper.ExcelMapper;
import cn.litblue.excel.utils.DataListListener;
import com.alibaba.excel.EasyExcel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author: litblue
 * @since: 2019/12/23 16:52
 */

@Service
public class ExcelService {

    @Resource
    private ExcelMapper excelMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;


    /**
     * 读取Excel，  并传入数据库
     * @param file
     * @return 是否成功导入数据库
     */
    public boolean importExcelByEasyExcel(MultipartFile file){
        try {
            // 这里 需要指定读用哪个class去读，然后读取第一个sheet
            // 需要将 ExcelMapper 传入构造器
            // doRead() 方法中有 finish() 方法，文件流会自动关闭

            /* 只读第一张工作表 */
            // EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(excelMapper)).sheet().doRead();
            // EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(jdbcTemplate)).sheet().doRead();


            // 读取所有工作表
            //EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(excelMapper)).doReadAll();
            EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(jdbcTemplate)).doReadAll();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
