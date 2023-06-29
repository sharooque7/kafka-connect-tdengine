package com.taosdata.kafka.connect.source;

import com.taosdata.kafka.connect.db.Processor;
import com.taosdata.kafka.connect.enums.OutputFormatEnum;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonMapper extends TableMapper {
    private static final Logger log = LoggerFactory.getLogger(JsonMapper.class);

    public JsonMapper(String topic, String tableName, int batchMaxRows, Processor processor) throws SQLException {
        super(topic, tableName, batchMaxRows, processor, OutputFormatEnum.JSON);
    }

    @Override
    public PendingRecord doExtractRecord(ResultSet resultSet, Map<String, String> partition) {
        List<TDStruct> structs = new ArrayList<>();
        TDStruct tagStruct = new TDStruct(tagBuilder.build());

        Timestamp ts = null;
        try {
            ts = resultSet.getTimestamp("_c0");
            long result = resultSet.getLong(1);
            if (result > 1_000_000_000_000_000_000L) {
                result = result / 1_000_000;
            } else if (result > 1_000_000_000_000_000L) {
                result = result / 1_000;
            }
            for (String tag : tags) {
                tagStruct.put(tag, getValue(resultSet, tag, columnType.get(tag)));
            }
            TDStruct valueStruct = new TDStruct(valueSchema);
            valueStruct.put(timestampColumn, result);
            for (String column : columns) {
                valueStruct.put(column, getValue(resultSet, column, columnType.get(column)));
            }
            if (!tags.isEmpty()) {
                valueStruct.put("tags", tagStruct);
            }
            structs.add(valueStruct);
        } catch (SQLException e) {
            log.error("resultSet get value error", e);
        }
        return new PendingRecord(partition, ts, topic, null, structs);
    }

    private Object getValue(ResultSet resultSet, String name, String type) throws SQLException {
        switch (type) {
            case "TINYINT":
                return resultSet.getByte(name);
            case "SMALLINT":
                return resultSet.getShort(name);
            case "INT":
                return resultSet.getInt(name);
            case "TIMESTAMP":
            case "BIGINT":
                return resultSet.getLong(name);
            case "FLOAT":
                return resultSet.getFloat(name);
            case "DOUBLE":
                return resultSet.getDouble(name);
            case "BOOL":
                return resultSet.getBoolean(name);
            case "NCHAR":
            case "JSON":
            case "BINARY":
            case "VARCHAR":
                return resultSet.getString(name);
            default:
                return null;
        }
    }
}
