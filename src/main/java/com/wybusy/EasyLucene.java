package com.wybusy;

import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class EasyLucene {
    private String path;
    private String name;
    private Directory directory;
    private SmartChineseAnalyzer analyzer;
    private IndexWriterConfig iwConfig;

    public String punctuation = "[\\[\\]\\{\\}\\s\\,\\.\\?!? ?—\":@\\*'\\-\\(\\)\\\\/;%（）。，：“”；！？、《》【】｛｝～＠＃￥％…＆×÷－＋＝\u3000‘’·]+";
    public String stripTag = "(<.*?>)";
    public Double luceneMaxMatchValue = 0.96;

    public EasyLucene(String path, String name) throws IOException {
        this.path = path;
        this.name = name;
        String direct = path + "/easylucene/" + name;
        directory = FSDirectory.open(Paths.get(direct));
        analyzer = new SmartChineseAnalyzer();
        iwConfig = new IndexWriterConfig(analyzer);
    }

    public Integer write(List<EasyLuceneData> data) {
        return write(data, true); //默认是追加
    }

    public Integer write(List<EasyLuceneData> data, boolean append) {
        if (append) { // 设置创建索引模式(无，创建后增加，有则在原来的索引的基础上新增)
            iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        } else {//创建索引，删除之前的索引
            iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        }
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
            e.printStackTrace();
        } finally {
            try {
                indexWriter.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        return result;
    }

    public boolean del(String id) {
        boolean result = true;
        IndexWriter indexWriter = null;
        try {
            indexWriter = new IndexWriter(directory, iwConfig);
            indexWriter.deleteDocuments(new Term("id", id));
            indexWriter.commit();
        } catch (IOException e) {
            result = false;
            e.printStackTrace();
        } finally {
            try {
                indexWriter.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
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

    public double similarity(String inputText, String storeText) {
        double result = 0;

        inputText = textToWord(inputText);
        storeText = textToWord(storeText);

        if (inputText != null && inputText.trim().length() > 0 && storeText != null
                && storeText.trim().length() > 0) {

            Map<String, int[]> AlgorithmMap = new HashMap<>();

            String[] input = inputText.toLowerCase().split(punctuation);
            String[] store = storeText.toLowerCase().split(punctuation);

            for (int i = 0; i < input.length; i++) {
                String d1 = input[i];
                int[] fq = AlgorithmMap.get(d1);
                if (fq != null && fq.length == 2) {
                    fq[0]++;
                } else {
                    fq = new int[2];
                    fq[0] = 1;
                    fq[1] = 0;
                    AlgorithmMap.put(d1, fq);
                }
            }

            for (int i = 0; i < store.length; i++) {
                String d2 = store[i];
                int[] fq = AlgorithmMap.get(d2);
                if (fq != null && fq.length == 2) {
                    fq[1]++;
                } else {
                    fq = new int[2];
                    fq[0] = 0;
                    fq[1] = 1;
                    AlgorithmMap.put(d2, fq);
                }
            }

            Iterator<String> iterator = AlgorithmMap.keySet().iterator();
            double sqdoc1 = 0;
            double sqdoc2 = 0;
            double denominator = 0;
            while (iterator.hasNext()) {
                int[] c = AlgorithmMap.get(iterator.next());
                denominator += c[0] * c[1];
                sqdoc1 += c[0] * c[0];
                sqdoc2 += c[1] * c[1];
            }

            result = denominator / Math.sqrt(sqdoc1 * sqdoc2);
        }
        return result;
    }

    public String textToWord(String text) {
        QueryParser parser = new QueryParser("", analyzer);
        String result = "";
        try {
            BooleanQuery.setMaxClauseCount(10000);
            Query query = parser.parse(text);
            result = query.toString();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        result = result.replaceAll("[\\(\\)]", "") //替换（）
                .replaceAll("\\d+", "") //替换数字
                .replaceAll(" \\w ", " "); //替换单个字母
        return result;
    }

}
