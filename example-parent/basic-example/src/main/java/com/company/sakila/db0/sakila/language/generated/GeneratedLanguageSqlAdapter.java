package com.company.sakila.db0.sakila.language.generated;

import com.company.sakila.db0.sakila.language.Language;
import com.company.sakila.db0.sakila.language.LanguageImpl;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.component.SqlAdapter;
import com.speedment.runtime.core.db.SqlFunction;
import java.sql.ResultSet;
import java.sql.SQLException;
import static com.speedment.common.injector.State.RESOLVED;

/**
 * The generated Sql Adapter for a {@link
 * com.company.sakila.db0.sakila.language.Language} entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public abstract class GeneratedLanguageSqlAdapter implements SqlAdapter<Language> {
    
    private final TableIdentifier<Language> tableIdentifier;
    
    protected GeneratedLanguageSqlAdapter() {
        this.tableIdentifier = TableIdentifier.of("db0", "sakila", "language");
    }
    
    protected Language apply(ResultSet resultSet, int offset) throws SQLException {
        return createEntity()
            .setLanguageId( resultSet.getShort(1 + offset))
            .setName(       resultSet.getString(2 + offset))
            .setLastUpdate( resultSet.getTimestamp(3 + offset))
            ;
    }
    
    protected LanguageImpl createEntity() {
        return new LanguageImpl();
    }
    
    @Override
    public TableIdentifier<Language> identifier() {
        return tableIdentifier;
    }
    
    @Override
    public SqlFunction<ResultSet, Language> entityMapper() {
        return entityMapper(0);
    }
    
    @Override
    public SqlFunction<ResultSet, Language> entityMapper(int offset) {
        return rs -> apply(rs, offset);
    }
}