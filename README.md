## 数十万Excel数据快速导入导出数据库

> 写在前面：读取Excel先后试用了Alibaba的开源框架`EasyExcel`和比较经典的`Poi`。使用poi过程中也走了很多弯路，在这里就不记录了，我还是弃用吧。其中遇到的最大的问题就是内存消耗过大，而EasyExcel很好的解决了这个问题。同时，EasyExcel 的代码量比较少，也比较易懂。关于EasyExcel网上有很多博客介绍，但是大多时间比较早，使用的方式已经过时。所以最好的方法就是直接去github 源仓库中看作者是如何写的。附上链接：https://github.com/alibaba/easyexcel。 这里的测试只是提供一些参考，结果肯定根据及其设备的不同而又差异，之前一个程序我在本机测试需要20秒，自己学生服务器需要40秒，但是部署到人家公司的服务器上2秒就完成了，数据量在5,6万条，10来列这样。

### 一、环境说明与准备

#### 1. 使用的工具及版本

+ 编译器： IDEA  2019.3 （破解码可以去我的博客中免费获取-->[JetBrains破解码](https://litblue.cn/upload/2019/7/%E6%BF%80%E6%B4%BB%E7%A0%8119041102-a5ee9668c25746a68d1fac2eebe8dabd.txt)）
+ 后端： SpringBoot 2.x
+ 数据库：MySQL 5.7
+ 解析Excel工具： EasyExcel 2.1.4

#### 2. 数据表

```sql
CREATE TABLE `excel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(10) DEFAULT NULL,
  `gender` varchar(5) DEFAULT NULL,
  `age` int(2) DEFAULT NULL,
  `remark` varchar(30) DEFAULT NULL,
  `uptime` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6581315 DEFAULT CHARSET=utf8mb4;

```

#### 3. Excel 表示例

| name    | gender | age  | remark                |
| ------- | ------ | ---- | --------------------- |
| litblue | 男     | 21   | #2019，你收获了什么？ |

+ 测试表有两张，一张2003版 xls 文件，共9张工作表 603075条数据。(xls文件一张工作表最多65536条数据)。另一张是2007版 xlsx文件，一张工作表 616192条数据。数据量差别不是很大，就姑且拿来做比较了。

#### 4. 目录结构

![img](http://qiniu.litblue.cn/content.png)

#### 5. 本案例代码仓库

[https://github.com/litblue/excel](https://github.com/litblue/excel)

### 二、读取Excel

#### 1. ArrayList && LinkedList

1. 简介与预想：

   这两种方法大家都很熟悉了，学习的时候说的都是：`ArrayList` 底层使用连续空间进行顺序存储，随机查询快O(1)，增加和删除慢。`LinkedList`底层使用双向队列实现，随机查询较慢,增删速度快。所以这里从Excel读取数据，按说是`LinkedList`较快，但是实际与预想的并不一样。

2. 实验：

   我直接使用最简单的`poi`读出Excel中的数据放在List集合中，共60000条数据，测试结果单位都是`ms`。一开始我用`ArrayList`，试验了6次，结果分别是：909,402,421,413,405,407。再使用`LinkedList`测试结果分别是：1259,1199,1198,1239,1229,1234。 

3. 分析：

   这里我看到一篇博客应该是能解答疑惑的：[ArrayList 与 LinkedList的效率实践分析](https://blog.csdn.net/u013504720/article/details/78685511)

   当数据量比较大的时候，`ArrayList`的内存管理采用内存扩容的方式，扩容时每次增加老容量的一半，然后直接插入数据。而`LinkedList`每次都要new一个新对象，修改链表之间的相互引用，一次性分配内存总会比多次分配内存花费的时间少。

4. EasyExcel中情况

   `EasyExcel`解析出来的数据默认是`ArrayList`，因为`ArrayList`和`LinkedList`不是父子类关系，也就不可以强制转换，那就不费那个心了。

#### 2. 读取工作表

```java
/* 只读第一张工作表 */
// EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(excelMapper)).sheet().doRead();
// EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(jdbcTemplate)).sheet().doRead();

/* 读取所有工作表 */
// EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(excelMapper)).doReadAll();
EasyExcel.read(file.getInputStream(), Excel.class, new DataListListener(jdbcTemplate)).doReadAll();
```

**解释：**

+ 这里列出了4种方法，前两种是 默认只读第一张工作表， 后两种 是读取所有的工作表。都通过MyBatis和JDBCTemplate实现存储数据的操作，对比性能。
+ 2003 版xls 一张工作表只能存储 65536行数据，2007及以后版本一个工作表最多可有1048576行。

####  3. 解析表格

```java
/**
 * 根据作者所说：
 * 这里的 DataListListener 不能被spring管理，要每次读取excel都要new,然后里面用到spring可以构造方法传进去
 *
 * @author: litblue
 * @since: 2019/12/23 18:42
 */

public class DataListListener extends AnalysisEventListener<Excel> {

    /**
     * 每隔3000条存储数据库，然后清理list ，方便内存回收
     */
    private static final int BATCH_COUNT = 3000;
    private List<Excel> excelList = new ArrayList<>();

    /**
     * 数据操作
     */
    private ExcelMapper excelMapper;
    private JdbcTemplate jdbcTemplate;


    /**
     * 自动注入的是null，所以通过构造器初始化 excelMapper
     * @param excelMapper
     */
    public DataListListener(ExcelMapper excelMapper){
        this.excelMapper = excelMapper;
    }

    /**
     * 自动注入的是null，所以通过构造器初始化 jdbcTemplate
     * @param jdbcTemplate
     */
    public DataListListener(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * 这个每一条数据解析都会来调用
     *
     * @param excel
     * @param analysisContext
     */
    @Override
    public void invoke(Excel excel, AnalysisContext analysisContext) {
        excelList.add(excel);

        // 达到BATCH_COUNT了，需要去存储一次数据库，防止数据几万条数据在内存，容易OOM
        if (excelList.size() >= BATCH_COUNT) {
            saveExcelByJdbcTemplate();
            // 存储完成清理 list
            excelList.clear();
        }
    }

    /**
     * 所有数据解析完成了，会来调用
     * 这里处理的是分批剩下的最后一批数据.
     *
     * @param analysisContext
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveExcelByJdbcTemplate();
    }

    /**
     * 批量存储数据
     * 通过 mybatis
     */
    private void saveExcelByMyBatis(){
        excelMapper.insertBatch(excelList);
    }

    /**
     * 批量存储数据
     * 通过 jdbcTemplate
     */
    private void saveExcelByJdbcTemplate(){
        new ExcelJdbcTemplate(jdbcTemplate).insertBatchByJdbcTemplate(excelList);
    }
}

```

批量存储数据，只调用其中的一个就好了。

### 三、插入数据库

#### 1. MyBatis

这里不再赘述，贴出一个博客，比较了三种数据插入的方式效率分析。结果是`foreach`批量插入方式效率最高。

[mybatis的三种批量插入以及次效率比较](https://www.cnblogs.com/gxyandwmm/p/9565002.html)

这里的实现：

```java
/**
 *  批量插入 数据
 * @param excelList
 */
@Insert({
    "<script>",
    " insert into `excel`(`name`, `gender`, `age`,`remark`, `uptime`) ",
    "values",
    " <foreach collection='excelList' item='excel' separator=','> ",
    "     (#{excel.name}, #{excel.gender}, #{excel.age}, #{excel.remark}, now()) ",
    " </foreach>",
    "</script>"
})
void insertBatch(@Param("excelList") List<Excel> excelList);
```

#### 2. JDBCTemplate

```java
 public void insertBatchByJdbcTemplate(List<Excel> excelList){
        String prefix = "insert delayed into `excel`(`name`, `gender`, `age`, `remark`, `uptime`) values";

        StringBuilder suffix  = new StringBuilder();

        for (Excel excel : excelList) {
            // 需要注意根据 字段的类型修改 单引号 '
            suffix .append("('").append(excel.getName()).append("','");
            suffix .append(excel.getGender()).append("',");
            suffix .append(excel.getAge()).append(",'");
            suffix .append(excel.getRemark()).append("',");
            suffix .append("now()),");
        }
        // 需要去除最后一个 ','
        jdbcTemplate.batchUpdate(prefix+suffix .substring(0,suffix .length()-1));
    }

```

原生的jdbc肯定要比框架效率来的高，而这里最影响效率的地方就是凭借字符串了，所以优化的地方就重点在字符串拼接。同样不再赘述，贴出他人写好的博客：

[Java 字符串拼接 五种方法的性能比较分析 从执行100次到90万次](https://www.cnblogs.com/twzheng/p/5923642.html)

为了直观，把这个博客中的图片拿出来贴一下：

![img](https://images2015.cnblogs.com/blog/21037/201609/21037-20160930143043922-459833518.gif)

#### 3. 效率比较

| 方式         | 表格类型 | 插入数据(行) | 耗时(ms) |
| ------------ | -------- | ------------ | -------- |
| MyBatis      | xls      | 603075       | 79648    |
| MyBatis      | xlsx     | 616192       | 94811    |
| JDBCTemplate | xls      | 603075       | 35166    |
| JDBCTemplate | xlsx     | 616192       | 44905    |

JDBCTemplate 明显比 MyBatis效率高。

#### 4. 其他优化方式

+ 配置my.ini文件

  给个我的my.ini文件地址：`C:\ProgramData\MySQL\MySQL Server 5.7`。网友有的说在`C:\ProgramFiles\`中，那是不对的。网上搜一搜好多都是使用的这种方式，我没有尝试，感觉应该不会太明显。还是给个链接吧。[优化MYSQL配置文件MY.INI](https://www.cnblogs.com/fan-yuan/p/8036388.html)。

+ 多线程

  下面详细介绍

### 四、使用Java Join/Fork 提升数据插入速率

fork/join可以让你把大的任务分解成一个个小任务，然后利用多线程能力，并发地跑这些任务，这样处理起来，就快很多了。如果每个小任务都有返回值，还可以把这些返回值汇总起来进行处理。有的ForkJoinTask子类还可以递归分解任务：如果大任务分解出来的小任务还不够小，就还可以继续分，一直递归。

这就是**“分而治之”**的思想，这里贴出一个使用方式的博客：[JAVA FORK-JOIN的使用例子](https://blog.csdn.net/ouyunwen/article/details/82846946)

#### 代码实现

这里给出的只是部分代码，详细情况请参考代码仓库。

```java
public class InsertBatchTask extends RecursiveAction {

    private JdbcTemplate jdbcTemplate;

    // 待插入数据
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

    /**
     * 通过Jdbc插入数据
     */
    private void saveExcelByJdbcTemplate(){
        new ExcelJdbcTemplate(jdbcTemplate).insertBatchByJdbcTemplate(excelList);
    }
}
```

```java
ForkJoinPool forkJoinPool = new ForkJoinPool(8);

 /**
  * 批量存储数据
  *
  * 依然通过JDBCTemplate
  * 但是使用join/fork工具
  */
private void saveExcelByJoinFork(){
    InsertBatchTask insertBatchTask = new InsertBatchTask(jdbcTemplate, excelList);
    forkJoinPool.invoke(insertBatchTask);
}
```

#### 测试结果

| 插入数据量 | 插入方式     | 耗时  |
| ---------- | ------------ | ----- |
| 616192     | JDBCTemplate | 26751 |

可以明显的看出时间缩短了将近20秒。

### 五、导出Excel

#### 实例代码：

Controller层：

```java
/**
 * 导出Excel
 * @param response
 * @param filename  导出文件名，默认 xlsx 文件
 * @param start 数据开始记录
 * @param end  数据结束记录
 */
@GetMapping("export")
public void exportExcelByEasyExcel(HttpServletResponse response, String filename, Integer start, Integer end){
    excelService.exportExcelByEasyExcel(response,filename, start, end);
}
```

Service 层：

```java
    /**
     * 导出excel
     */
    public void exportExcelByEasyExcel(HttpServletResponse response, String filename, Integer start, Integer end){
        //List<Excel> excelList = excelMapper.selectAll();

        List<Excel> excelList = new ExcelJdbcTemplate(jdbcTemplate).selectExcel(start, end);
        try {
            ExcelWriter writer = EasyExcel.write(getOutputStream(filename, response),Excel.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").build();
            writer.write(excelList, writeSheet);
            /// 千万别忘记finish 会帮忙关闭流
            writer.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    } 

    /**
     * 导出文件头信息 设置
     *
     * @param filename
     * @param response
     * @return
     * @throws Exception
     */
    public static OutputStream getOutputStream(String filename, HttpServletResponse response) throws Exception {
        try {
            filename = URLEncoder.encode(filename, "UTF-8");
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".xlsx");
            response.setHeader("Pragma", "public");
            response.setHeader("Cache-Control", "no-store");
            response.addHeader("Cache-Control", "max-age=0");
            return response.getOutputStream();
        } catch (IOException e) {
            throw new Exception("导出excel表格失败!", e);
        }
    }
```

Dao层：还是使用JDBCTemplate

```java
public List<Excel> selectExcel(Integer start, Integer end){
        StringBuilder sql = new StringBuilder();
        sql.append("select `name`,`gender`,`age`,`remark` from `excel` ")
                .append("limit ?,? ");

        List<Excel> excels =  jdbcTemplate.query(sql.toString(),new Object[]{start,end}, new BeanPropertyRowMapper<>(Excel.class));

        return excels;
    }
```

#### 耗时

这里只使用了JDBCTempalate的方式，因为使用MyBatis的时候，内存溢出了。这一是下一步优化时要考虑的问题。

| 数据量 | 耗时     |
| ------ | -------- |
| 600000 | 79636 ms |

#### 优化

主要耗时都花费在从数据库读取数据了，读取就大概花费了60000ms了。所以这就是下一步优化的重点。从网上逛了一下，发现一篇写的不错而且感觉比较靠谱的方式：[Java用多线程批次查询大量数据（Callable返回数据）方式](https://blog.csdn.net/dfBeautifulLive/article/details/82788830)。但是这篇博客中也有不妥当之处！根据评论进行测试，发现以下问题：

![img](http://qiniu.litblue.cn/callable.png)

根据博客中的写法，这是放在构造器中的。我测试时大概是60万数据用了42秒，还是比较慢。但是放在如上图的位置，也就是call()方法中，速度为7.7秒。可以说是很明显的区别了。

### 六、优化查询的具体过程

查询语句：

```java
public List<Excel> selectExcel(Integer start, Integer rows){
    StringBuilder sql = new StringBuilder();
    sql.append("select `name`,`gender`,`age`,`remark` from `excel` ")
        .append("limit ?,? ");

    List<Excel> excels =  jdbcTemplate.query(sql.toString(),new Object[]{start,rows}, new BeanPropertyRowMapper<>(Excel.class));

    return excels;
}
```

多线程执行查询操作：

```java
public class ThreadQuery implements Callable<List<Excel>> {

    private ExcelJdbcTemplate excelJdbcTemplate;

    private int start;//当前页数

    private int rows;//每页查询多少条


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
```

服务层调用：

```java
/**
 * 查询数据
 * @return
 * @throws InterruptedException
 * @throws ExecutionException
 */
public List<Excel> queryExcelByThread() throws InterruptedException, ExecutionException {
    List<Excel> excelList = new ArrayList<>();

    int count = 600000;

    // 一次查询多少条
    int rows = 8000;

    //需要查询的次数
    int times = count / rows;
    if (count % rows != 0) {
        times = times + 1;
    }

    int start = 1;

    List<Callable<List<Excel>>> tasks = new ArrayList<>();

    for (int i=0;i<times;i++){
        Callable<List<Excel>> listCallable = new ThreadQuery(jdbcTemplate,start,rows);
        tasks.add(listCallable);

        start += rows;
    }

    //定义固定长度的线程池  防止线程过多
    ExecutorService executorService = Executors.newFixedThreadPool(8);

    List<Future<List<Excel>>> futures = executorService.invokeAll(tasks);

    if (futures.size() > 0){
        for (Future<List<Excel>> future : futures) {
            excelList.addAll(future.get());
        }
    }

    executorService.shutdown();

    return excelList;
}

```

### 最后

因为测试的表格图省事都是一样的数据，所以可能出现数据不一致问题。导入是在实际开发中用过的，应该没有什么问题，导出可能在读取数据时有些许偏颇。如果有什么问题，欢迎一起进行讨论。还是小菜鸟一枚，多线程等理解颇浅，如有错误，还请指正。

