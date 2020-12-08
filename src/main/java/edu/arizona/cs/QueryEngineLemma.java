
/*=============================================================================
 |   Assignment:  Assignment 3
 |       Author:  Kinsleigh Wong
 |        NetID:  kinsleighwong
 |
 |       Course:  CSC 583
 |   Instructor:  Mihai Surdeanu
 |          TAs:  Mithun Paul
 |     Due Date:  10/28/2020, 11:59pm
 +-----------------------------------------------------------------------------
 |  Description: This class is meant to create a Lucene index given a set 
 |               of documents along with the words within the documents. 
 |              
 *===========================================================================*/
package edu.arizona.cs;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Pattern; 
// stuff i added

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.util.Properties;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

public class QueryEngineLemma {
    //relevant files
    String inputFilePath ="training";
    String indexDir = "index_lemma";
    String questionFile = "questions.txt";
    // for Lucene
    StandardAnalyzer analyzer = new StandardAnalyzer();
    Directory index;
    // for Stanford core nlp
    Properties props;
    StanfordCoreNLP pipeline;

    public QueryEngineLemma() {
        props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        pipeline = new StanfordCoreNLP(props);
        try {
            File indDir = new File(getClass().getClassLoader().getResource(indexDir).getFile());
            this.index = FSDirectory.open(indDir.toPath());

            if(indDir.listFiles().length  == 0) {
                buildIndex();            
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String processTitle(String title) {
        return title.split("\\(")[0];
    }


    public double processQuestions() throws IOException {
        File input = new File(getClass().getClassLoader().getResource(questionFile).getFile());
        int successes = 0, fails = 0, count = 0;
        String query = "", topResult = null;
        try (Scanner inputScanner = new Scanner(input)) {
            String line = null;
            while (inputScanner.hasNextLine()) {
                line = inputScanner.nextLine().trim();
                
                if(line.length() == 0) {
                    continue;
                } else if(count == 0) {
                    query = processTitle(line);
                } else if(count == 1) {
                    query += (" " + line);
                } else {
                    try {
                        topResult = search(query);
                        //System.out.println("topResult: " + topResult);  

                    } catch(Exception e) {
                        System.out.println("SEARCHING FOR QUERY FAILED");
                        e.printStackTrace();
                    }                    
                    
                    String[] answers = line.split("\\|");
                    int i = 0;
                    for(i = 0; i < answers.length; ++i) {
                        //should i make things case-insensitive?
                        System.out.println(answers[i] + " vs " + topResult);
                        if(answers[i].compareTo(topResult) == 0) {
                         successes++;
                         break;
                        }
                    }

                    if(i >= answers.length) {
                        fails++;
                    }
                    
                    count = -1;
                }
                count++;
                
            }
            inputScanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(fails == 0) {
            System.out.println("IMPOSSIBLE");
            fails = 1; 
        }
        System.out.println("Successes: " + successes);
        System.out.println("Fails: " + fails);
        return ((double) successes) / (successes + fails);
    }

    private void addArticle(String article, String contents) throws IOException {
        if(article.equals("") || contents.equals("")) {
            return;
        }

        //Initialize and build our index. 
        CoreDocument document = pipeline.processToCoreDocument(contents);
        contents = "";
        for(CoreLabel tok : document.tokens()) {
            contents += (" " + tok.lemma());
        }
        

        IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
        IndexWriter w = new IndexWriter(this.index, config);
        Document doc = new Document();
        doc.add(new StringField("docid", article, Field.Store.YES));
        doc.add(new TextField("body", contents, Field.Store.YES));
        w.addDocument(doc);
        w.close();
    }

    private String processLine(String line) {
        //get rid of citations
        return line.replaceAll("\\[tpl\\].*\\[\\/tpl\\]", "");
    }

    private void buildIndex() throws IOException {
        //read files from resource folder
        ClassLoader classLoader = getClass().getClassLoader();
        File dir = new File(classLoader.getResource(inputFilePath).getFile());

        System.out.println(dir.isFile() + " " + dir.isDirectory());

        File fileList[] = dir.listFiles();
        for (File file : fileList) {
            System.out.println(file);
            try (Scanner inputScanner = new Scanner(file)) {
                String line = null, articleName = "", content = "";
                Boolean addArticle = true, addSection = true;
                while (inputScanner.hasNextLine()) {
                    line = inputScanner.nextLine().trim();
                    if(line.length() == 0)
                        continue;
                    
                    if(line.startsWith("[[") && !line.startsWith("[[File") && !line.startsWith("[[Image")) {
                        if(addArticle)
                            addArticle(articleName, content);
                        articleName = line.replaceAll("[\\[\\]]", "");
                        content = "";
                        addArticle = true;
                        addSection = true;
                    } else if(line.startsWith("#REDIRECT")
                        || line.startsWith("May refer to")) {
                        addArticle = false; 
                    } else if(line.startsWith("==")) {
                        if(line.equals("==See also==")
                            || line.equals("==References==")
                            || line.equals("==External links==")
                            || line.equals("==Further reading==")) {
                            addSection = false;
                        } else {
                            addSection = true; 
                        }
                    } else if(addArticle && addSection) {
                        content += (processLine(line) + " ");
                    }
                }

                if(addArticle) {
                    addArticle(articleName, content);
                }
                inputScanner.close();
            } catch (IOException e) {
                System.out.println("oh no");
                e.printStackTrace();
            }

        }
    }


    //this is the search function used for question 1
   public String search(String query) throws Exception {
        int hitsPerPage = 10;
        QueryParser parser = new QueryParser("body", analyzer);

        //Initialize and build our index. 
        CoreDocument document = pipeline.processToCoreDocument(query);
        query = "";
        for(CoreLabel tok : document.tokens()) {
            query += (" " + tok.lemma());
        }    


        Query q = parser.parse(parser.escape(query));

        IndexReader reader = DirectoryReader.open(this.index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;
        
        String hit = searcher.doc(hits[0].doc).get("docid");
        reader.close();

        
        return hit;
    }

}
