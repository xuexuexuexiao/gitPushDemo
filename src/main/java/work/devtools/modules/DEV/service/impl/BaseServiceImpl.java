package work.devtools.modules.DEV.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.SQLQuery;
import org.hibernate.transform.Transformers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import work.devtools.common.domain.web.QueryParams;
import work.devtools.common.domain.web.constants.Constants;
import work.devtools.common.utils.SpringUtil;
import work.devtools.common.utils.StringUtil;
import work.devtools.modules.DEV.dao.T_queryDao;
import work.devtools.modules.DEV.dao.T_querylineDao;
import work.devtools.modules.DEV.pojo.dto.Dev_tableField;
import work.devtools.modules.DEV.pojo.po.T_query;
import work.devtools.modules.DEV.pojo.po.T_queryline;
import work.devtools.modules.DEV.service.BaseService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Huxiaoxue
 * @version V1.0
 * @ClassName
 * @desc:
 * @create: 2019-04-17
 **/
@Service
@Slf4j
public class BaseServiceImpl<T> implements BaseService{

    @PersistenceContext
    private  EntityManager entityManager;


    public  Page<T> query(String queryCode, QueryParams queryParams){
        // 根据查询编号 通过 t_query表 得到： 查询的表  以及order by 的条件
        T_queryDao t_queryDao = (T_queryDao) SpringUtil.getBean("t_queryDao");
        T_query t_query = t_queryDao.findByQuery01(queryCode);
        //from  SQL
        String fromSQL = t_query.getQuery03();
        //where SQL
        String whereSQL = t_query.getQuery04();
        //order by SQL
        String orderBySQL = t_query.getQuery05();

        // 根据查询编号 通过t_queryLine表 得到字段栏位， 得到where条件 ，以及每个查询参数在where条件的位置
        T_querylineDao t_querylineDao = SpringUtil.getBean(T_querylineDao.class);

        StringBuilder sb = new StringBuilder();
        sb.append("select ");
        List<T_queryline> querylineList =  t_querylineDao.findByQueryline01(queryCode);

        StringBuilder whereSb = new StringBuilder();
        whereSb.append(" where 1=1 ");
        StringBuilder whereSb1 = new StringBuilder();
        //存放占位符的Map
        Map<String,Object> argWhereMap = new HashMap<String,Object>();
        //字段
        for (int i = 0 ; i<querylineList.size() ; i++){
            T_queryline t_queryline = querylineList.get(i);

            //where条件所在的位置
            String queryline09 = t_queryline.getQueryline09();

            //对应的字段
            String queryline06 = t_queryline.getQueryline06();
            //拼接SQL 字段
            sb.append(t_queryline.getQueryline04()+"."+ queryline06 +" , ");
            //数据库类型
            String queryline07 = t_queryline.getQueryline07();
            //表别名
            String queryline04 = t_queryline.getQueryline04();


            //前端传递的值 : 比如 <10001  ,将这种<10001做替换成mySql 的语法
            Object o = queryParams.get(queryline06);
            if(o != null){

                String s = (String) o;
                if(StringUtil.isNotBlank(s)){
                    if(argWhereMap.get(queryline09) == null){
                        whereSb1 = new StringBuilder();
                    }
                    //input 转换为 sqlStr   TODO 需要判断字段的类型
                    String sqlStr = inputVal2SqlStr(s,queryline07);
                    whereSb1.append(" "+queryline04+"."+queryline06 + sqlStr+" ");
                    //存放whereSQL
                    argWhereMap.put(queryline09,whereSb1.toString());

                }
            }


        }
        int i = sb.lastIndexOf(",");
        sb = sb.deleteCharAt(i);
        sb.append(fromSQL+" ");
        sb.append(whereSb);

        Set<String> strings = argWhereMap.keySet();
        //拼接whereSQL
        for(String str: strings){
            sb.append(" and ("+argWhereMap.get(str)+") ");
            sb.append(" or ");
        }
        int lastIndexOr = sb.lastIndexOf("or");
        sb = sb.delete(lastIndexOr, sb.length());
        sb.append(orderBySQL);

        log.info("执行的SQL ：  "+sb.toString());

        boolean checkInject = checkSQLInject(sb.toString());

        if(checkInject){
            //TODO  抛出异常
//            throw new Exception("查询异常，请联系IT部门！");
        }

        /**
         * 执行SQL
         */
        Query nativeQuery = entityManager.createNativeQuery(sb.toString());
        nativeQuery.unwrap(SQLQuery.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
        List<T> resultList = nativeQuery.getResultList();


        int offset = 0;
        int pageSize = 10;
        if(StringUtil.isNotBlank(String.valueOf( queryParams.get("offSet")))){
            nativeQuery.setFirstResult((int) queryParams.get("offSet"));
            offset = (int) queryParams.get("offSet");
        }
        if(StringUtil.isNotBlank(String.valueOf(queryParams.get("pageSize")))){
            nativeQuery.setMaxResults((int) queryParams.get("pageSize"));
            pageSize = (int) queryParams.get("pageSize");
        }


        //封装到Page对象
        Page<T> page =new PageImpl<T>(resultList,new PageRequest(offset,pageSize),resultList.size());
        return page;
    }

    /**
     * 检查SQL注入
     * @param str
     * @return
     */
    private boolean checkSQLInject(String str) {
        //可能注入的字符串
        String injectStr = "|insert|delete|update|truncate|";
        String[] split = injectStr.split("|");
        for(int i=0 ; i<split.length ; i++){
            if(str.indexOf(split[i])>0){
                return true;
            }
        }
        return false;
    }

    /**
     * 将查询参数转换为sql语句
     * @param str  输入的查询参数 eg: <10000
     * @return
     */
    private String inputVal2SqlStr(String str,String queryline07) {
        //执行顺序 1、= ； 2、> ；3< ;4、>= 5、<=;6、 <> 7、% 8、"_" 8、
        //第一个字符
        String regx = "\\*{0,1}[a-zA-Z0-9\\u4E00-\\u9FA5]+\\*{0,1}";
        String regx2 = "(\\?){0,}[a-zA-Z0-9\\u4E00-\\u9FA5]+(\\?){0,}";
        String regx3 = "[a-zA-Z0-9\\u4E00-\\u9FA5]+[:][a-zA-Z0-9\\u4E00-\\u9FA5]+";
        String regx4 = "[a-zA-Z0-9\\u4E00-\\u9FA5]+[\\|][a-zA-Z0-9\\u4E00-\\u9FA5]+";

        String firstChar = str.substring(0,1);
        //末尾字符
        String lastChar = str.substring(str.length() - 1);

        switch(firstChar)
        {
            case Constants.SIGNSTR.EQ :
            case Constants.SIGNSTR.GT :
            case Constants.SIGNSTR.LT :
            case Constants.SIGNSTR.GE :
            case Constants.SIGNSTR.LE :
            case Constants.SIGNSTR.NEQ:
                if(Pattern.matches(Constants.DATATYPE.STR_REG,queryline07)){
                    return "'"+str.substring(0)+"' ";
                }else{
                    return ""+str.substring(0);
                }
        }
        //输入如: *123  123* *123*
        if(Constants.SIGNSTR.STAR.equals(firstChar)||Constants.SIGNSTR.STAR.equals(lastChar)){
            if(Pattern.matches(regx,str)){
                str = " like '" + str.replace("*","%") +"' ";
            }

        }else if(Constants.SIGNSTR.QUESTION.equals(firstChar)||Constants.SIGNSTR.QUESTION.equals(lastChar)){
            if(Pattern.matches(regx2,str)){
                str = " like '"+str.replace("?","_")+"' ";
            }
        }else if(str.contains(Constants.SIGNSTR.COLON)){
            if(Pattern.matches(regx3,str)){
                str = " between '"+str.substring(0,str.indexOf(":"))+ "' and  '"+ str.substring(str.indexOf(":")+1)+"'";
            }
        }else if(str.contains(Constants.SIGNSTR.VERTICAL_BAR)){
            if(Pattern.matches(regx4,str)){
                str = str.replace("|",",");
                str  = " in (" + str + ")";
            }
        }else{
            if(Pattern.matches(Constants.DATATYPE.STR_REG,str)){
                str = " = '"+str.substring(0)+"' ";
                return str = " = '"+str.substring(0)+"' ";
            }else{

                return str = " = '"+str.substring(0)+"' ";
            }
        }
        return str;
    }


    //TODO  文件上传

}
