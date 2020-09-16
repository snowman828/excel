package cn.litblue.excel.utils;

import cn.litblue.excel.entity.Excel;
import cn.litblue.excel.mapper.ExcelJdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.RecursiveAction;

/**
 *
 * 批量插入数据
 *
 * @author: litblue
 * @since: 2019/12/24 8:30
 */

@Slf4j
public class InsertBatchTask extends RecursiveAction {

    private JdbcTemplate jdbcTemplate;

    /** 待插入数据  */
    List<Excel> excelList;

    // 每一批次插入的数据
    private final static int BATCH_SIZE = 3000;

    public InsertBatchTask(JdbcTemplate jdbcTemplate,List<Excel> excelList){
        this.jdbcTemplate = jdbcTemplate;
        this.excelList = excelList;
    }


    @Override
    protected void compute() {



        // 当要插入的数据<1500,则直接插入
        if (excelList.size() <= BATCH_SIZE){
            saveExcelByJdbcTemplate();
        } else {

            int size = excelList.size();

            // 进行分组
            InsertBatchTask insertBatchTask1 = new InsertBatchTask(jdbcTemplate,excelList.subList(0,size/2));
            InsertBatchTask insertBatchTask2 = new InsertBatchTask(jdbcTemplate,excelList.subList(size/2,size));

            // 任务并发执行
            invokeAll(insertBatchTask1, insertBatchTask2);

        }
    }


    private void saveExcelByJdbcTemplate(){
        new ExcelJdbcTemplate(jdbcTemplate).insertBatchByJdbcTemplate(excelList);
    }

}
