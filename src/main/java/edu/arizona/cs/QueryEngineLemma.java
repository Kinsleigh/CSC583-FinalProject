/*=============================================================================
 |   Assignment:  Final Project
 |        Class:  QueryEngineLemma
 |       Author:  Kinsleigh Wong
 |        NetID:  kinsleighwong
 |
 |       Course:  CSC 583
 |   Instructor:  Mihai Surdeanu
 |          TAs:  Mithun Paul
 |     Due Date:  12/9/2020, 11:59pm
 +-----------------------------------------------------------------------------
 |  Description: This class implements an index for the Final Project with the
 |               grad portion and lemmatization implemented.
 |              
 *===========================================================================*/
package edu.arizona.cs;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Arrays;
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
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;


import java.util.Properties;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

public class QueryEngineLemma {
    String categories[] = new String[]{"CITY", "STATE_OR_PROVINCE"};
    HashSet<String> categorySet;
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

    //variables used to determine if we display the score
    double score = 0.0;
    Boolean scoringOn = true;

    public QueryEngineLemma() {
        categorySet = new HashSet<String>(Arrays.asList(categories));
        props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,regexner,depparse");
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
        String[] res = title.split("\\(");
        if(res.length > 1) {
            String info = res[1].replaceAll("[()]", "").split(":")[1];

            //Initialize and build our index. 
            CoreDocument document = pipeline.processToCoreDocument(info);
            SemanticGraph tree = null;
            IndexedWord word = null, currWord = null;
            CoreLabel tok = null;
            String label = "";
            if(document.sentences().size() <= 1) {
                return "";
            }
            //System.out.println(info);
            for (CoreSentence sent : document.sentences()) {
                tree = sent.dependencyParse();
                //System.out.println(tree);
                //going through every category,
                for(int i = 0; i < categories.length; ++i) {
                    //going trhough every token in the sentence,
                    for(CoreLabel token : sent.tokens()) {
                        //if we find a word that matches one of our categories,
                        if(categories[i].contains(token.word().toUpperCase())) {
                            //we look at the sentence structure. 
                            for(SemanticGraphEdge edge : tree.getOutEdgesSorted(tree.getFirstRoot())) {
                                //System.out.println(edge.getRelation().getShortName() + " " + edge.getDependent().word());
                                //if we determine that the task wants US to identify,
                                if(edge.getRelation().getShortName().equals("nsubj")
                                    && edge.getDependent().word().toUpperCase().equals("YOU")) {
                                        //we take note
                                        //System.out.println("We have found the label: " + categories[i]);                
                                        return categories[i];
                                    }
                            }
                        } 
                    }
                }       

            }
        }
        return "";
    }


    public double processQuestions() throws IOException {
        File input = new File(getClass().getClassLoader().getResource(questionFile).getFile());
        int successes = 0, fails = 0, count = 0;
        String query = "", topResult = null, metadata = "", content = "";

        try (Scanner inputScanner = new Scanner(input)) {
            String line = null;
            while (inputScanner.hasNextLine()) {
                line = inputScanner.nextLine().trim();
                
                if(line.length() == 0) {
                    continue;
                } else if(count == 0) {
                    query = line.split("\\(")[0]; //processTitle(line);
                    metadata = processTitle(line);
                } else if(count == 1) {
                    query += (" " + line);
                    content = line;
                } else {
                    try {
                        if(search(query, line, metadata, content)) {
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
        doc.add(new TextField("article", article, Field.Store.YES));
        
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
   public Boolean search(String query, String answer, String metadata, String content) throws Exception {
        int hitsPerPage = 20;
        String hit = "";
        Boolean retVal = false, firstFound = true;
        CoreDocument document = pipeline.processToCoreDocument(query);

        query = "";
        for(CoreLabel tok : document.tokens()) {
            query += (" " + tok.lemma());
        }
        //System.out.println(query);
        IndexReader reader = DirectoryReader.open(this.index);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BooleanSimilarity());
        //searcher.setSimilarity(new ClassicSimilarity());
        String[] answers = answer.split("\\|");


        if(!metadata.equals("")) {
            //System.out.println("METADATA: " + metadata);
            QueryParser parser = new QueryParser("body", analyzer);
            Query q = parser.parse(query);
            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            hit = searcher.doc(hits[0].doc).get("docid");
            Boolean exists = false, done = false;
            CoreLabel tok = null;
            String new_hit = "";
            for(int docInd = 0; docInd < hits.length && new_hit.equals(""); ++docInd) {
                CoreDocument new_doc = pipeline.processToCoreDocument(searcher.doc(hits[docInd].doc).get("body"));
                for (CoreSentence sent : new_doc.sentences()) {
                    exists = false;
                    done = false;
                    List<CoreLabel> tokens = sent.tokens();
                    for(int i = 0; i < tokens.size(); ++i) {
                        tok = tokens.get(i);
                        //System.out.println(tok.word() + " ");
                        if(tok.word().equals("in") || tok.word().equals("at")) {
                            exists = true;
                        }

                        //if we find a word that matches one of our categories,
                        if(exists && !done 
                            && tok.ner() != null && tok.ner().equals(metadata)) {
                            new_hit = tok.word();
                            done = true;
                        } else if(exists && done 
                            && tok.ner() != null && tok.ner().equals(metadata)) {
                            new_hit += (" " + tok.word());
                        } else if(exists && done 
                            && (tok.ner() == null || !tok.ner().equals(metadata))) {
                            break;
                        }
                    }

                    if(done) {
                        for(int j = 0; j < answers.length; ++j) {
                            //if we found a match, we calculate the score.
                            if(answers[j].compareTo(hit) == 0) {
                                System.out.println("Results number " + docInd + " matched " + answers[j] + "\n");
                                score += ( 1 / (double) (docInd + 1));
                                reader.close();
                                return firstFound;
                            }
                        }
                        if(firstFound)
                            hit = new_hit;
                       firstFound = false; 
                       if(!scoringOn)
                        break;
                    }
                }
            }
            if(!new_hit.equals("")) {
                hit = new_hit;
            }

            System.out.println("First hit: " + hit);

            for(int i = 0; i < answers.length; ++i) {
                if(answers[i].compareTo(hit) == 0) {
                    System.out.println("First hit, gotem!\n");
                    score += 1;
                    reader.close();
                    return true;
                }
            }

            System.out.println("No matches were found in top twenty.");
            System.out.println("Actual answer: " + answer + "\n");

            reader.close();
        } else {
            QueryParser parser = new QueryParser("body", analyzer);
            Query q = parser.parse(parser.escape(query));

            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            //determine whether first hit is a match or not
            hit = searcher.doc(hits[0].doc).get("docid");
            System.out.println("First hit: " + hit);

            for(int i = 0; i < answers.length; ++i) {
                if(answers[i].compareTo(hit) == 0) {
                    retVal = true;
                    break;
                }
            }

            if(scoringOn) {
                //going through all the top 20 relevant documents, 
                for(int i = 0; i < hits.length; ++i) {
                    hit = searcher.doc(hits[i].doc).get("docid");
                    //going through all the possible answers,
                    for(int j = 0; j < answers.length; ++j) {
                        //if we found a match, we calculate the score.
                        if(answers[j].compareTo(hit) == 0) {
                            System.out.println("Results number " + i + " matched " + answers[j] + "\n");
                            score += ( 1 / (double) (i + 1));
                            reader.close();
                            return retVal;
                        }
                    }
                }
                System.out.println("No matches were found in top twenty.");
                System.out.println("Actual answer: " + answer + "\n");
            } else {
                System.out.println(hit + " vs " + answer + "\n");
            }

            reader.close();
        }
        
        return retVal;
   }
    //this is the search function used for questions part 3
    public Boolean search_modified(String query, String answer) throws Exception {
        int hitsPerPage = 20;
        QueryParser parser = new QueryParser("body", analyzer);
        Boolean retVal = false;

        //Initialize and build our index. 
        CoreDocument document = pipeline.processToCoreDocument(query);
        query = "";
        for(CoreLabel tok : document.tokens()) {
            query += (" " + tok.lemma());
        }    
        Query q = parser.parse(parser.escape(query));


        IndexReader reader = DirectoryReader.open(this.index);
        IndexSearcher searcher = new IndexSearcher(reader);
        //ClassicSimilarity is the implementation of TFIDFSimilarity
        searcher.setSimilarity(new BooleanSimilarity());
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;        

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
                //System.out.print(hit + ", ");
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
            System.out.println("\nNo matches were found in top twenty.");
            System.out.println("Actual answer: " + answer + "\n");
        }
        reader.close();

        return retVal;
    }

}
