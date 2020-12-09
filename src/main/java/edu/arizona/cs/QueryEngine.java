
/*=============================================================================
 |   Assignment:  Final Project
 |        Class:  QueryEngine
 |       Author:  Kinsleigh Wong
 |        NetID:  kinsleighwong
 |
 |       Course:  CSC 583
 |   Instructor:  Mihai Surdeanu
 |          TAs:  Mithun Paul
 |     Due Date:  12/9/2020, 11:59pm
 +-----------------------------------------------------------------------------
 |  Description: This class implements an index for the Final Project without
 |               lemmatization nor stemming. It reads from the folder "indexes"
 |               in the resources, and creates the index if that folder is 
 |                empty. 
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
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;


public class QueryEngine {
    //variables that control where to read from/write to
    String inputFilePath ="training";
    String indexDir = "indexes";
    String questionFile = "questions.txt";

    //folder for Lucene
    StandardAnalyzer analyzer = new StandardAnalyzer();
    Directory index;

    //variables used to determine if we display the score
    double score = 0.0;
    Boolean scoringOn = true;

    public QueryEngine() {
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
                        if(search_modified(query, line)) {
                        //if(search(query, line)) {
                            successes++;
                        } else {
                            fails++;
                        }

                    } catch(Exception e) {
                        System.out.println("SEARCHING FOR QUERY FAILED");
                        e.printStackTrace();
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
        System.out.println("Score: " + score / (successes + fails));
        return ((double) successes) / (successes + fails);
    }

    private void addArticle(String article, String contents) throws IOException {
        if(article.equals("") || contents.equals("")) {
            return;
        }

        //Initialize and build our index. 
        
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
                    if(line.length() == 0 || line.startsWith("[[File") || line.startsWith("[[Image"))
                        continue;
                    
                    if(line.startsWith("[[")) {
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
   public Boolean search(String query, String answer) throws Exception {
        System.out.println(query);
        int hitsPerPage = 20;
        QueryParser parser = new QueryParser("body", analyzer);
        Query q = parser.parse(parser.escape(query));
        Boolean retVal = false;

        IndexReader reader = DirectoryReader.open(this.index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(q, hitsPerPage);


        ScoreDoc[] hits = docs.scoreDocs;
        //determine whether first hit is a match or not
        String hit = searcher.doc(hits[0].doc).get("docid");
        System.out.print("First hit: " + hit);
        String[] answers = answer.split("\\|");
        for(int i = 0; i < answers.length; ++i) {
            if(answers[i].compareTo(hit) == 0) {
                //System.out.println(answers[i] + " " + hit);
                retVal = true;
                break;
            }
        }

        //we measure performanced with Mean Reciprocal Rank (MRR)
        //going through all the top 20 relevant documents, 
        if(scoringOn) {
        for(int i=0; i < hits.length; ++i) {
            hit = searcher.doc(hits[i].doc).get("docid");
            System.out.print(hit + ", ");
            //going through all the possible answers,
            for(int j = 0; j < answers.length; ++j) {
                //if we found a match, we calculate the score.
                if(answers[j].compareTo(hit) == 0) {
                    System.out.println("\nResults number " + i + " matched " + answers[j] + "\n");
                    score += ( 1 / (double) (i + 1));
                    reader.close();
                    return retVal;
                }
            }
        }
        //if we find the top 20 hits were a miss, we consider it to be have failed.     
        System.out.println("\nNo matches were found in top twenty.");
        System.out.println("Actual answer: " + answer + "\n");
        }


        reader.close();
        return retVal;
    }

    //this is the search function used for questions part 3
    public Boolean search_modified(String query, String answer) throws Exception {
        int hitsPerPage = 10;
        QueryParser parser = new QueryParser("body", analyzer);
        Query q = parser.parse(parser.escape(query));
        Boolean retVal = false;

        IndexReader reader = DirectoryReader.open(this.index);
        IndexSearcher searcher = new IndexSearcher(reader);
        //ClassicSimilarity is the implementation of TFIDFSimilarity
        searcher.setSimilarity(new BooleanSimilarity());
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;        

        String hit = searcher.doc(hits[0].doc).get("docid");
        System.out.print("First hit: " + hit);

        //determine whether first hit is a match or not
        String[] answers = answer.split("\\|");
        for(int i = 0; i < answers.length; ++i) {
            if(answers[i].compareTo(hit) == 0) {
                System.out.println(answers[i] + " " + hit);
                retVal = true;
                break;
            }
        }

        //we measure performanced with Mean Reciprocal Rank (MRR)
        //going through all the top 20 relevant documents, 
        if(scoringOn) {
        for(int i=0; i < hits.length; ++i) {
            hit = searcher.doc(hits[i].doc).get("docid");
            //going through all the possible answers,
            for(int j = 0; j < answers.length; ++j) {
                //if we found a match, we calculate the score.
                if(answers[j].compareTo(hit) == 0) {
                    System.out.println("\nResults number " + i + " matched " + answers[j] + "\n");
                    score += ( 1 / (double) (i + 1));
                    reader.close();
                    return retVal;
                }
            }
        }
        //if we find the top 20 hits were a miss, we consider it to be have failed.     
        System.out.println("\nNo matches were found in top twenty.");
        System.out.println("Actual answer: " + answer + "\n");
        }
        reader.close();

        return retVal;
    }

}
