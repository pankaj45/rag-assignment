package com.example.secfix.chunker;

import java.util.List;

public interface TextChunker {

    List<Chunk> chunk(String text, int pageNum);
}
