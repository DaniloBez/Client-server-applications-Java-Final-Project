package utils;

import java.util.ArrayList;
import java.util.List;

public class SqlQueryBuilder {
    private final String baseQuery;

    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();

    private final List<Object> whereParams = new ArrayList<>();

    private Integer limitValue = null;
    private Integer offsetValue = null;

    public SqlQueryBuilder(String baseQuery) {
        this.baseQuery = baseQuery;
    }

    public SqlQueryBuilder whereEqual(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " = ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereNotEqual(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " != ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereAnyColumnEqual(Object value, String... columns) {
        if (value != null && columns != null && columns.length > 0) {
            StringBuilder orClause = new StringBuilder("(");

            for (int i = 0; i < columns.length; i++) {
                orClause.append(columns[i]).append(" = ?");
                whereParams.add(value);

                if (i < columns.length - 1)
                    orClause.append(" OR ");
            }
            orClause.append(")");

            whereClauses.add(orClause.toString());
        }
        return this;
    }

    public SqlQueryBuilder whereIsNull(String column) {
        whereClauses.add(column + " IS NULL");
        return this;
    }

    public SqlQueryBuilder whereIsNotNull(String column) {
        whereClauses.add(column + " IS NOT NULL");
        return this;
    }

    public SqlQueryBuilder whereGreaterThan(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " > ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereGreaterOrEqual(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " >= ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereLessThan(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " < ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereLessOrEqual(String column, Object value) {
        if (value != null) {
            whereClauses.add(column + " <= ?");
            whereParams.add(value);
        }
        return this;
    }

    public SqlQueryBuilder whereBetween(String column, Object minValue, Object maxValue) {
        if (minValue != null && maxValue != null) {
            whereClauses.add(column + " BETWEEN ? AND ?");
            whereParams.add(minValue);
            whereParams.add(maxValue);
        }
        return this;
    }

    public SqlQueryBuilder whereLike(String column, String value) {
        if (value != null && !value.trim().isEmpty()) {
            whereClauses.add(column + " LIKE ?");
            whereParams.add("%" + value + "%");
        }
        return this;
    }

    public SqlQueryBuilder whereIlike(String column, String value) {
        if (value != null && !value.isEmpty()) {
            whereClauses.add(column + " ILIKE ?");
            whereParams.add("%" + value + "%");
        }
        return this;
    }

    public SqlQueryBuilder whereStartsWith(String column, String value) {
        if (value != null && !value.trim().isEmpty()) {
            whereClauses.add(column + " ILIKE ?");
            whereParams.add(value + "%");
        }
        return this;
    }

    public SqlQueryBuilder whereEndsWith(String column, String value) {
        if (value != null && !value.trim().isEmpty()) {
            whereClauses.add(column + " ILIKE ?");
            whereParams.add("%" + value);
        }
        return this;
    }

    public SqlQueryBuilder whereIn(String column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            StringBuilder placeholders = new StringBuilder();
            for (Object value : values) {
                placeholders.append("?,");
                whereParams.add(value);
            }
            placeholders.deleteCharAt(placeholders.length() - 1);
            whereClauses.add(column + " IN (" + placeholders + ")");
        }
        return this;
    }

    public SqlQueryBuilder whereNotIn(String column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            StringBuilder placeholders = new StringBuilder();
            for (Object value : values) {
                placeholders.append("?,");
                whereParams.add(value);
            }
            placeholders.deleteCharAt(placeholders.length() - 1);
            whereClauses.add(column + " NOT IN (" + placeholders + ")");
        }
        return this;
    }

    public SqlQueryBuilder orderByAsc(String column) {
        orderByClauses.add(column + " ASC");
        return this;
    }

    public SqlQueryBuilder orderByDesc(String column) {
        orderByClauses.add(column + " DESC");
        return this;
    }

    public SqlQueryBuilder limit(Integer limit) {
        if (limit != null && limit > 0) {
            this.limitValue = limit;
        }
        return this;
    }

    public SqlQueryBuilder offset(Integer offset) {
        if (offset != null && offset >= 0) {
            this.offsetValue = offset;
        }
        return this;
    }

    public SqlQueryBuilder paginate(Integer page, Integer size) {
        if (page != null && page > 0 && size != null && size > 0) {
            this.limitValue = size;
            this.offsetValue = (page - 1) * size;
        }
        return this;
    }

    public String getSql() {
        StringBuilder finalSql = new StringBuilder(baseQuery);
        if (!whereClauses.isEmpty()) {
            if (baseQuery.toUpperCase().contains("WHERE"))
                finalSql.append(" AND ");
            else
                finalSql.append(" WHERE ");

            finalSql.append(String.join(" AND ", whereClauses));
        }

        if (!orderByClauses.isEmpty())
            finalSql.append(" ORDER BY ").append(String.join(", ", orderByClauses));


        if (limitValue != null) finalSql.append(" LIMIT ?");
        if (offsetValue != null) finalSql.append(" OFFSET ?");

        return finalSql.toString();
    }

    public List<Object> getParams() {
        List<Object> finalParams = new ArrayList<>(this.whereParams);

        if (limitValue != null) finalParams.add(limitValue);
        if (offsetValue != null) finalParams.add(offsetValue);

        return finalParams;
    }
}