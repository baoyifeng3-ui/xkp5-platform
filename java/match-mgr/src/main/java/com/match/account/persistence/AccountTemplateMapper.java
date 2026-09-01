package com.match.account.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import org.apache.ibatis.annotations.Select; import java.util.List;
public interface AccountTemplateMapper extends BaseMapper<AccountTemplateRecord> { @Select("SELECT * FROM account_template ORDER BY template_name") List<AccountTemplateRecord> selectAllOrdered(); }
