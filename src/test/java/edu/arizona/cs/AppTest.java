package edu.arizona.cs;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.io.IOException;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        //QueryEngine test1 = new QueryEngine();
        //QueryEngineLemma test2 = new QueryEngineLemma();
        //QueryEngineStem test3 = new QueryEngineStem();

        QueryEngineSimplest test = new QueryEngineSimplest();
        
        double res = 0.0;

        
        try {   /*         
            res = test1.processQuestions();
            System.out.println("Normal: " + res);
            System.out.println("**************************************************************************************************************\n");
            
            res = test2.processQuestions();
            System.out.println("Lemma: " + res);
            System.out.println("**************************************************************************************************************\n");
            
            res = test3.processQuestions();
            System.out.println("Stem: " + res);
            System.out.println("**************************************************************************************************************\n");
            */
            res = test.processQuestions();
            System.out.println("Success to Failure Ratio: " + res);
            System.out.println("**************************************************************************************************************\n");
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertTrue( true );
    }
}
