/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package org.lealone.transaction.aote.log;

import org.lealone.db.DataBuffer;
import org.lealone.db.value.ValueString;
import org.lealone.storage.StorageMap;
import org.lealone.transaction.aote.AOTransactionEngine;
import org.lealone.transaction.aote.TransactionalValue;
import org.lealone.transaction.aote.TransactionalValueType;

public class UndoLogRecord {

    private final String mapName;
    private Object key; // 没有用final，在AMTransaction.replicationPrepareCommit方法那里有特殊用途
    private final Object oldValue;
    private final TransactionalValue newTV;
    private volatile boolean undone;

    UndoLogRecord next;
    UndoLogRecord prev;

    public UndoLogRecord(String mapName, Object key, Object oldValue, TransactionalValue newTV) {
        this.mapName = mapName;
        this.key = key;
        this.oldValue = oldValue;
        this.newTV = newTV;
    }

    public String getMapName() {
        return mapName;
    }

    public UndoLogRecord getNext() {
        return next;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setUndone(boolean undone) {
        this.undone = undone;
    }

    // 调用这个方法时事务已经提交，redo日志已经写完，这里只是在内存中更新到最新值
    public void commit(AOTransactionEngine transactionEngine) {
        if (undone)
            return;
        StorageMap<Object, TransactionalValue> map = transactionEngine.getStorageMap(mapName);
        if (map == null) {
            return; // map was later removed
        }
        if (oldValue == null) { // insert
            newTV.commit(true);
        } else if (newTV != null && newTV.getValue() == null) { // delete
            if (!transactionEngine.containsRepeatableReadTransactions()) {
                map.remove(key);
            } else {
                map.decrementSize(); // 要减去1
                newTV.commit(false);
                map.put(key, newTV, ar -> {
                });
            }
        } else { // update
            newTV.commit(false);
            // TODO 如果不put回去存储引擎不知道数据发生变化了，会丢失更新的数据
            // 是否可以考虑在TransactionalValue中增加page ref，然后调用markDirty方法，但是这种方案会增加内存开销
            map.put(key, newTV, ar -> {
            });
            // TODO
            // 先删除后增加的场景，需要重新put回去
            // if (newValue.getOldValue() != null && newValue.getOldValue().getValue() == null) {
            // map.put(key, ref);
            // }
        }
    }

    // 当前事务开始rollback了，调用这个方法在内存中撤销之前的更新
    public void rollback(AOTransactionEngine transactionEngine) {
        if (undone)
            return;
        StorageMap<Object, TransactionalValue> map = transactionEngine.getStorageMap(mapName);
        // 有可能在执行DROP DATABASE时删除了
        if (map != null) {
            if (oldValue == null) {
                map.remove(key);
            } else {
                newTV.rollback(oldValue);
            }
        }
    }

    // 用于redo时，不关心oldValue
    public void writeForRedo(DataBuffer writeBuffer, AOTransactionEngine transactionEngine) {
        if (undone)
            return;
        StorageMap<?, ?> map = transactionEngine.getStorageMap(mapName);
        // 有可能在执行DROP DATABASE时删除了
        if (map == null) {
            return;
        }
        int lastPosition = writeBuffer.position();

        ValueString.type.write(writeBuffer, mapName);
        int keyValueLengthStartPos = writeBuffer.position();
        writeBuffer.putInt(0);

        map.getKeyType().write(writeBuffer, key);
        if (newTV.getValue() == null)
            writeBuffer.put((byte) 0);
        else {
            writeBuffer.put((byte) 1);
            // 如果这里运行时出现了cast异常，可能是上层应用没有通过TransactionMap提供的api来写入最初的数据
            ((TransactionalValueType) map.getValueType()).valueType.write(writeBuffer, newTV.getValue());
        }
        writeBuffer.putInt(keyValueLengthStartPos, writeBuffer.position() - keyValueLengthStartPos - 4);

        // 预估一下内存占用大小，当到达一个阈值时方便其他服务线程刷数据到硬盘
        int memory = writeBuffer.position() - lastPosition;
        transactionEngine.incrementEstimatedMemory(mapName, memory);
    }
}
