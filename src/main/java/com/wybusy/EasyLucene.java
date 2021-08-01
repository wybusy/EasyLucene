package com.wybusy;

import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EasyLucene {
    private String path;
    private String name;
    private Directory directory;
    private SmartChineseAnalyzer analyzer;
    private IndexWriterConfig iwConfig;

    public EasyLucene(String path, String name) throws IOException {
        this.path = path;
        this.name = name;
        String direct = path + "/easylucene/" + name;
        directory = FSDirectory.open(Paths.get(direct));
        analyzer = new SmartChineseAnalyzer();
        iwConfig = new IndexWriterConfig(analyzer);
    }

    public Integer append(List<EasyLuceneData> data) {
        // 设置创建索引模式(无，创建后增加，有则在原来的索引的基础上新增)
        iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return write(data);
    }

    public Integer creat(List<EasyLuceneData> data) {
        //创建索引，删除之前的索引
        iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        return write(data);
    }

    private Integer write(List<EasyLuceneData> data) {
        Integer result = 0;
        IndexWriter indexWriter = null;
        try {
            indexWriter = new IndexWriter(directory, iwConfig);
            for (EasyLuceneData item : data) {
                Document doc = new Document();
                // TextField 即创建索引，又会被分词。(多用于内容)
                // StringField 创建索引，但是不会被分词。(多用于主键)
                doc.add(new StringField("id", item.getId(), Field.Store.YES));
                doc.add(new TextField("content", item.getContent(), Field.Store.YES));
                doc.add(new StringField("json", item.getJson(), Field.Store.YES));
                // 新添加一个doc对象
                indexWriter.addDocument(doc);
            }
            // 创建的索引数目
            result = indexWriter.numRamDocs();
            // 提交事务
            indexWriter.commit();
            // 关闭事务
            indexWriter.close();
        } catch (IOException e) {
            if (indexWriter != null) {
                try {
                    indexWriter.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            e.printStackTrace();
        }
        return result;
    }

    public List<EasyLuceneData> search(String text, Integer number) {
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);
            // 搜索含有text的内容
            Query query = parser.parse(text);
            // 搜索标题和显示条数
            TopDocs tds = searcher.search(query, number);
            List<EasyLuceneData> list = new ArrayList<>();
            // 在内容中查获找
            for (ScoreDoc sd : tds.scoreDocs) {
                EasyLuceneData item = new EasyLuceneData();
                item.setId(searcher.doc(sd.doc).get("id"));
                // 获取json
                item.setJson(searcher.doc(sd.doc).get("json"));
                // 获取content
                String content = searcher.doc(sd.doc).get("content");
                // 内容添加高亮
                QueryParser qp = new QueryParser("content", analyzer);
                // 将匹配到的text添加高亮处理
                Query q = qp.parse(text);
                String highlightContent = displayHtmlHighlight(q, "content", content);

                item.setContent(content);
                item.setHighLightContent(highlightContent);
                item.setScore(sd.score);
                list.add(item);
            }
            return list;
        } catch (Exception e) {
        }
        return null;
    }

    public String displayHtmlHighlight(Query query, String fieldName, String fieldContent)
            throws IOException, InvalidTokenOffsetsException {
        // 设置高亮标签,可以自定义,这里我用html将其显示为红色
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<font color='red'>", "</font>");
        // 评分
        QueryScorer scorer = new QueryScorer(query);
        // 创建Fragmenter
        Fragmenter fragmenter = new SimpleSpanFragmenter(scorer);
        // 高亮分析器
        Highlighter highlight = new Highlighter(formatter, scorer);
        highlight.setTextFragmenter(fragmenter);
        // 调用高亮方法
        String str = highlight.getBestFragment(analyzer, fieldName, fieldContent);
        return str;
    }
}
