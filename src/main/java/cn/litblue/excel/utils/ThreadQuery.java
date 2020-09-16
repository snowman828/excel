package cn.litblue.excel.utils;

import cn.litblue.excel.entity.Excel;
import cn.litblue.excel.mapper.ExcelJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author: litblue
 * @since: 2019/12/24 16:53
 */

public class ThreadQuery implements Callable<List<Excel>> {


    private final ExcelJdbcTemplate excelJdbcTemplate;

    /** 当前页数 */
    private final int start;

    /** 每页查询多少条 */
    private final int rows;


    public ThreadQuery(JdbcTemplate jdbcTemplate, int start, int rows) {
        this.start = start;
        this.rows = rows;

        excelJdbcTemplate = new ExcelJdbcTemplate(jdbcTemplate);

    }


    @Override
    public List<Excel> call() throws Exception {
        //分页查询数据库数据
        return excelJdbcTemplate.selectExcel(start, rows);
    }
}
