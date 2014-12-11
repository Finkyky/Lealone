/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.cluster.utils;

import java.io.DataInput;
import java.io.IOException;

import org.lealone.cluster.db.TypeSizes;
import org.lealone.cluster.io.ISerializer;
import org.lealone.cluster.io.util.DataOutputPlus;
import org.lealone.cluster.utils.obs.IBitSet;
import org.lealone.cluster.utils.obs.OpenBitSet;

abstract class BloomFilterSerializer implements ISerializer<BloomFilter> {
    @Override
    public void serialize(BloomFilter bf, DataOutputPlus out) throws IOException {
        out.writeInt(bf.hashCount);
        bf.bitset.serialize(out);
    }

    @Override
    public BloomFilter deserialize(DataInput in) throws IOException {
        return deserialize(in, false);
    }

    public BloomFilter deserialize(DataInput in, boolean offheap) throws IOException {
        int hashes = in.readInt();
        //IBitSet bs = offheap ? OffHeapBitSet.deserialize(in) : OpenBitSet.deserialize(in);
        IBitSet bs = OpenBitSet.deserialize(in);
        return createFilter(hashes, bs);
    }

    protected abstract BloomFilter createFilter(int hashes, IBitSet bs);

    /**
     * Calculates a serialized size of the given Bloom Filter
     * @see org.lealone.cluster.io.ISerializer#serialize(Object, org.lealone.cluster.io.util.DataOutputPlus)
     *
     * @param bf Bloom filter to calculate serialized size
     *
     * @return serialized size of the given bloom filter
     */
    @Override
    public long serializedSize(BloomFilter bf, TypeSizes typeSizes) {
        int size = typeSizes.sizeof(bf.hashCount); // hash count
        size += bf.bitset.serializedSize(typeSizes);
        return size;
    }
}
