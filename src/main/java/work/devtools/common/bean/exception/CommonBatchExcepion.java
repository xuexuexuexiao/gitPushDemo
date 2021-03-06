package work.devtools.common.bean.exception;

import lombok.Data;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @Description:  自定义批量的异常类(回滚)
* @Author:         CPQ
* @date:     2019/4/16 下午 4:59
* @Version:        1.0
*/
@Data
public class CommonBatchExcepion extends RuntimeException {
    /* code为于前端约定好的批量异常code,暂定为：BatchException*/
    private String code = "BatchException";
    /* 异常数组*/
    private List<BatchExceptionBean> list;

    public CommonBatchExcepion(List<BatchExceptionBean> list){
        super("");
        this.list = list;
    }
}
