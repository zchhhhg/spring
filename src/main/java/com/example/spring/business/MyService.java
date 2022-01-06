package com.example.spring.business;

import com.example.spring.event.EventPublisher;
import com.example.spring.util.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Cheng
 * @date 2021-09-08-10:32
 */
@Service
public class MyService implements IService {

    /**
     * define a global Logger object
     */
    private static Logger logger = LoggerFactory.getLogger(MyService.class);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 由BUSI_CODE关联的配置表，置于字符串数组中方便批量操作
     */
    private static String[] tables = {"OPP_BUSI_DEF","OPP_BUSI_ACS_CFG","OPP_PROC_POST_CFG","OPP_BUSI_REVIEW_NODE"};

    @Override
    public void getCache() {
        String result = getCacheResult();
        logger.debug(result);
        logger.info("测算项目【{}】明细中部门经济分类【{}】的测算金额【{}】元","我的项目","0503-公用经费","123");
        logger.warn(result);
        logger.error(result);
    }

    public String getCacheResult(){
        return "cache result";
    }

    @Override
    public void publishEvent(){
        Map<String,Object> param = new HashMap<>();
        param.put("message", "233333");
        eventPublisher.publish(param, "001");
        logger.info("我在事件发布之后");
    }
    @Override
    public void generateSqlScript(String MENU_ID) throws Exception {
        Map<String, Object> menuInfo = jdbcTemplate.queryForMap("SELECT * FROM UPM_MENU WHERE MENU_ID="+MENU_ID);
        if(CommonUtil.isNullOrEmpty(menuInfo)){
            throw new Exception("菜单信息不存在!!!");
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(generateScript("UPM_MENU", MENU_ID, null, null));
        if(CommonUtil.isNotNullOrEmpty(menuInfo.get("BUSI_CODE"))){
            String BUSI_CODE = CommonUtil.nonNullStr(menuInfo.get("BUSI_CODE"));
            List<Map<String, Object>> checkList = jdbcTemplate.queryForList("SELECT * FROM OPP_BUSI_ACS_CFG WHERE BUSI_CODE='"+BUSI_CODE+"'");
            if(CommonUtil.isNotNullOrEmpty(checkList)){
                checkList.forEach(e->{
                    String CON_ID = CommonUtil.nonNullStr(e.get("CON_ID"));
                    try {
                        stringBuilder.append(generateScript("OPP_BUSI_ACS_COND", MENU_ID,null,CON_ID));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
            }
            for(int j = 0; j< tables.length; j++) {
                String tableName = tables[j];
                stringBuilder.append(generateScript(tableName, MENU_ID, BUSI_CODE, null));
            }
        }
        System.out.println(stringBuilder);
        writeToTxtFile(stringBuilder);
    }

    private StringBuilder generateScript(String tableName,String MENU_ID,String BUSI_CODE,String CON_ID) throws IOException {
        String whereStr = " WHERE MENU_ID=" + MENU_ID;
        if(CommonUtil.isNotNullOrEmpty(BUSI_CODE)){
            whereStr = " WHERE BUSI_CODE='" + BUSI_CODE + "'";
        }
        if(CommonUtil.isNotNullOrEmpty(CON_ID)){
            whereStr = " WHERE CON_ID=" + CON_ID;
        }
        List<Map<String, Object>> dataList = jdbcTemplate.queryForList("SELECT * FROM " + tableName + whereStr);
        if (CommonUtil.isNotNullOrEmpty(dataList)) {
            StringBuilder sb = new StringBuilder("DELETE FROM ").append(tableName).append(whereStr).append(";\n");
            List<Map<String, Object>> columnList = jdbcTemplate.queryForList("SELECT ColumnName = Columns.name ,ColumnType = Types.name FROM sys.tables AS Tables INNER JOIN sys.columns AS Columns ON Tables.object_id = Columns.object_id INNER JOIN sys.types AS Types ON Columns.system_type_id = Types.system_type_id AND is_user_defined = 0 AND Types.name <> 'sysname' WHERE Tables.name = '" + tableName + "' ORDER BY Columns.column_id;");
            for (int k = 0; k < dataList.size(); k++) {
                Map<String, Object> map = dataList.get(k);
                StringBuilder fieldSb = new StringBuilder("INSERT INTO " + tableName + " (");
                StringBuilder valueSb = new StringBuilder(" VALUES (");
                for (int i = 0; i < columnList.size(); i++) {
                    String columnName = CommonUtil.nonNullStr(columnList.get(i).get("ColumnName"));
                    String columnType = CommonUtil.nonNullStr(columnList.get(i).get("ColumnType"));
                    fieldSb.append(columnName).append(",");
                    if (CommonUtil.isNullOrEmpty(map.get(columnName))) { // 若当前field值为空，value直接给空串
                        valueSb.append("'',");
                        continue;
                    }
                    if ("int".equals(columnType) || "numeric".equals(columnType)) { // 整型及数字型的值拼接时不加字符串
                        valueSb.append(map.get(columnName)).append(",");
                    } else if("datetime".equals(columnType)){
                        valueSb.append("GETDATE(),");
                    } else {
                        valueSb.append("'").append(map.get(columnName)).append("',");
                    }
                }
                fieldSb.replace(fieldSb.length() - 1, fieldSb.length(), ")");
                valueSb.replace(valueSb.length() - 1, valueSb.length(), ")");
                sb.append(fieldSb).append(valueSb).append(";\n");
            }
            return sb.append("\n");
        }
        return new StringBuilder();
    }
    private void writeToTxtFile(StringBuilder sb) throws IOException {
        String filenameTemp = FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath()+File.separator+"script.txt";
        File filename = new File(filenameTemp);
        if (!filename.exists()) {
            filename.createNewFile();
        }
        // 先读取原有文件内容，然后进行写入操作
        String temp = "";
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        FileOutputStream fos = null;
        PrintWriter pw = null;
        try {
            // 文件路径
            File file = new File(filenameTemp);
            // 将文件读入输入流
            fis = new FileInputStream(file);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);
            StringBuffer buf = new StringBuffer();
            // 保存该文件原有的内容
//            for (int j = 1; (temp = br.readLine()) != null; j++) {
//                buf = buf.append(temp);
//                // System.getProperty("line.separator")
//                // 行与行之间的分隔符 相当于“\n”
//                buf = buf.append(System.getProperty("line.separator"));
//            }
            buf.append(sb.toString());
            fos = new FileOutputStream(file);
            pw = new PrintWriter(fos);
            pw.write(buf.toString().toCharArray());
            pw.flush();
        } catch (IOException e1) {
            throw e1;
        } finally {
            if (pw != null) {
                pw.close();
            }
            if (fos != null) {
                fos.close();
            }
            if (br != null) {
                br.close();
            }
            if (isr != null) {
                isr.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }
}
