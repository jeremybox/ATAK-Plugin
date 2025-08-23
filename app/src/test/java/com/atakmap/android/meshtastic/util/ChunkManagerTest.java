package com.atakmap.android.meshtastic.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

@ExtendWith(MockitoExtension.class)
class ChunkManagerTest {

    @Mock
    private IMeshService meshService;

    @Mock
    private SharedPreferences sharedPreferences;

    @Mock
    private SharedPreferences.Editor editor;

    private ChunkManager chunkManager;

    @BeforeEach
    void setUp() {
        chunkManager = new ChunkManager();
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putInt(anyString(), anyInt())).thenReturn(editor);
        when(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor);
    }

    @Test
    void shouldDivideDataIntoChunks() {
        // Given
        byte[] data = new byte[500];
        for (int i = 0; i < 500; i++) {
            data[i] = (byte) (i % 256);
        }

        // When
        List<byte[]> chunks = chunkManager.divideIntoChunks(data);

        // Then
        assertThat(chunks).hasSize(3); // 500 / 200 = 2.5, so 3 chunks
        assertThat(chunks.get(0)).hasSize(200);
        assertThat(chunks.get(1)).hasSize(200);
        assertThat(chunks.get(2)).hasSize(100);
    }

    @Test
    void shouldHandleEmptyData() {
        // Given
        byte[] data = new byte[0];

        // When
        List<byte[]> chunks = chunkManager.divideIntoChunks(data);

        // Then
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldHandleDataSmallerThanChunkSize() {
        // Given
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);

        // When
        List<byte[]> chunks = chunkManager.divideIntoChunks(data);

        // Then
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(data);
    }

    @Test
    void shouldCreateChunkHeader() {
        // Given
        int totalSize = 1024;

        // When
        byte[] header = chunkManager.createChunkHeader(totalSize);

        // Then
        String headerStr = new String(header, StandardCharsets.UTF_8);
        assertThat(headerStr).isEqualTo("CHK_1024_");
    }

    @Test
    void shouldCombineHeaderAndChunk() {
        // Given
        byte[] header = "HEADER_".getBytes(StandardCharsets.UTF_8);
        byte[] chunk = "CHUNK_DATA".getBytes(StandardCharsets.UTF_8);

        // When
        byte[] combined = chunkManager.combineHeaderAndChunk(header, chunk);

        // Then
        assertThat(combined).hasSize(header.length + chunk.length);
        String combinedStr = new String(combined, StandardCharsets.UTF_8);
        assertThat(combinedStr).isEqualTo("HEADER_CHUNK_DATA");
    }

    @Test
    void shouldRejectSendingNullData() throws Exception {
        // When & Then
        boolean result = chunkManager.sendChunkedData(null, meshService, 
                sharedPreferences, 3, 0);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectSendingEmptyData() throws Exception {
        // When & Then
        boolean result = chunkManager.sendChunkedData(new byte[0], meshService, 
                sharedPreferences, 3, 0);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectSendingDataExceedingMaxSize() throws Exception {
        // Given
        byte[] largeData = new byte[11 * 1024 * 1024]; // 11MB

        // When & Then
        boolean result = chunkManager.sendChunkedData(largeData, meshService, 
                sharedPreferences, 3, 0);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectSendingWithNullMeshService() throws Exception {
        // Given
        byte[] data = "Test data".getBytes(StandardCharsets.UTF_8);

        // When & Then
        boolean result = chunkManager.sendChunkedData(data, null, 
                sharedPreferences, 3, 0);
        assertThat(result).isFalse();
    }

    @Test
    void shouldStartReceivingWithValidSize() {
        // When
        chunkManager.startReceiving(1000);

        // Then
        assertThat(chunkManager.isReceiving()).isTrue();
    }

    @Test
    void shouldRejectReceivingWithInvalidSize() {
        // When
        chunkManager.startReceiving(-1);

        // Then
        assertThat(chunkManager.isReceiving()).isFalse();
    }

    @Test
    void shouldRejectReceivingWithExcessiveSize() {
        // When
        chunkManager.startReceiving(11 * 1024 * 1024);

        // Then
        assertThat(chunkManager.isReceiving()).isFalse();
    }

    @Test
    void shouldAddReceivedChunksAndAssemble() {
        // Given
        byte[] chunk1 = "Hello ".getBytes(StandardCharsets.UTF_8);
        byte[] chunk2 = "World".getBytes(StandardCharsets.UTF_8);
        int totalSize = chunk1.length + chunk2.length;
        
        // When
        chunkManager.startReceiving(totalSize);
        boolean complete1 = chunkManager.addReceivedChunk(0, chunk1);
        boolean complete2 = chunkManager.addReceivedChunk(1, chunk2);

        // Then
        assertThat(complete1).isFalse();
        assertThat(complete2).isTrue();

        byte[] assembled = chunkManager.assembleChunks();
        assertThat(assembled).isNotNull();
        assertThat(new String(assembled, StandardCharsets.UTF_8)).isEqualTo("Hello World");
    }

    @Test
    void shouldHandleOutOfOrderChunks() {
        // Given
        byte[] chunk1 = "Hello ".getBytes(StandardCharsets.UTF_8);
        byte[] chunk2 = "World".getBytes(StandardCharsets.UTF_8);
        int totalSize = chunk1.length + chunk2.length;
        
        // When
        chunkManager.startReceiving(totalSize);
        boolean complete1 = chunkManager.addReceivedChunk(1, chunk2); // Add chunk 2 first
        boolean complete2 = chunkManager.addReceivedChunk(0, chunk1); // Then chunk 1

        // Then
        assertThat(complete1).isFalse();
        assertThat(complete2).isTrue();

        byte[] assembled = chunkManager.assembleChunks();
        assertThat(assembled).isNotNull();
        assertThat(new String(assembled, StandardCharsets.UTF_8)).isEqualTo("Hello World");
    }

    @Test
    void shouldResetProperly() {
        // Given
        chunkManager.startReceiving(100);
        chunkManager.addReceivedChunk(0, new byte[50]);

        // When
        chunkManager.reset();

        // Then
        assertThat(chunkManager.isReceiving()).isFalse();
        assertThat(chunkManager.assembleChunks()).isNull();
    }

    @Test
    void shouldNotAddChunkWhenNotReceiving() {
        // Given
        byte[] chunk = "Test".getBytes(StandardCharsets.UTF_8);

        // When
        boolean result = chunkManager.addReceivedChunk(0, chunk);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotAssembleIncompleteChunks() {
        // Given
        chunkManager.startReceiving(400); // Expecting 2 chunks of 200 bytes each
        chunkManager.addReceivedChunk(0, new byte[200]);

        // When
        byte[] assembled = chunkManager.assembleChunks();

        // Then
        assertThat(assembled).isNull();
    }

    @Test
    void shouldHandleCustomChunkSize() {
        // Given
        ChunkManager customChunkManager = new ChunkManager(100);
        byte[] data = new byte[250];

        // When
        List<byte[]> chunks = customChunkManager.divideIntoChunks(data);

        // Then
        assertThat(chunks).hasSize(3); // 250 / 100 = 2.5, so 3 chunks
        assertThat(chunks.get(0)).hasSize(100);
        assertThat(chunks.get(1)).hasSize(100);
        assertThat(chunks.get(2)).hasSize(50);
    }
}