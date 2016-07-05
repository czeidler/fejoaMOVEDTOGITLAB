/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class ChunkHash {
    private class Layer {
        final ChunkSplitter splitter;
        Layer upperLayer;
        MessageDigest hash;
        // hash of the first chunk, only if there are more then one chunk an upper layer is started
        byte[] firstChunkHash;

        public Layer(ChunkSplitter splitter) {
            this.splitter = splitter;
        }

        void update(byte... data) {
            if (hash == null)
                hash = getMessageDigest();

            hash.update(data);
            for (byte b : data)
                splitter.update(b);

            if (splitter.isTriggered()) {
                splitter.reset();
                finalizeChunk();
            }
        }

        private void finalizeChunk() {
            if (hash == null)
                return;

            byte[] chunkHash = hash.digest();

            if (firstChunkHash == null && upperLayer == null)
                firstChunkHash = chunkHash;
            else {
                Layer upper = ensureUpperLayer();
                if (firstChunkHash != null) {
                    upper.update(firstChunkHash);
                    firstChunkHash = null;
                }
                upper.update(chunkHash);
            }
            hash = null;
        }

        public byte[] digest() {
            finalizeChunk();
            if (firstChunkHash != null)
                return firstChunkHash;
            // empty data
            if (upperLayer == null)
                return new byte[0];
            return upperLayer.digest();
        }

        Layer ensureUpperLayer() {
            if (upperLayer == null)
                upperLayer = new Layer(newNodeSplitter());
            return upperLayer;
        }
    }

    final private ChunkSplitter dataSplitter;
    final private ChunkSplitter nodeSplitter;
    private Layer currentLayer;

    public ChunkHash(ChunkSplitter dataSplitter, ChunkSplitter nodeSplitter) throws NoSuchAlgorithmException {
        this.dataSplitter = dataSplitter;
        this.nodeSplitter = nodeSplitter;
        reset();
        // test for message digest
        getMessageDigestRaw();
    }

    private MessageDigest getMessageDigestRaw() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private MessageDigest getMessageDigest() {
        try {
            return getMessageDigestRaw();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(byte[] data) {
        for (byte b : data)
            update(b);
    }

    public void update(byte data) {
        currentLayer.update(data);
    }

    public byte[] digest() {
        return currentLayer.digest();
    }

    public void reset() {
        this.currentLayer = new Layer(dataSplitter);
    }

    protected ChunkSplitter newNodeSplitter() {
        return nodeSplitter.newInstance();
    }
}
