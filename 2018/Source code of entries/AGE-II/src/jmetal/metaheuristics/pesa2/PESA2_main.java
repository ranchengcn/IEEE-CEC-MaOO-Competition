/**
 * PESA2_main.java
 *
 * @author Juanjo Durillo
 * @version 1.0
 * 
 * This class executes the PESA2 algorithm
 */
package jmetal.metaheuristics.pesa2;

import jmetal.base.*;
import jmetal.base.operator.crossover.*   ;
import jmetal.base.operator.mutation.*    ; 
import jmetal.base.operator.selection.*   ;
import jmetal.base.variable.*             ;
import jmetal.problems.*                  ;
import jmetal.problems.DTLZ.*;
import jmetal.problems.ZDT.*;
import jmetal.problems.WFG.*;
import jmetal.qualityIndicator.QualityIndicator;

import jmetal.util.Configuration;
import jmetal.util.JMException;
import java.io.IOException;

import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class PESA2_main {
  public static Logger      logger_ ;      // Logger object
  public static FileHandler fileHandler_ ; // FileHandler object
  
  /**
   * @param args Command line arguments. The first (optional) argument specifies 
   *             the problem to solve.
   * @throws JMException 
   * @throws IOException 
   * @throws SecurityException 
   * Usage: three options
   *      - jmetal.metaheuristics.mocell.MOCell_main
   *      - jmetal.metaheuristics.mocell.MOCell_main problemName
   *      - jmetal.metaheuristics.mocell.MOCell_main problemName ParetoFrontFile
   */
  public static void main(String [] args) throws JMException, IOException, ClassNotFoundException {
    Problem   problem   ;         // The problem to solve
    Algorithm algorithm ;         // The algorithm to use
    Operator  crossover ;         // Crossover operator
    Operator  mutation  ;         // Mutation operator    
     
    QualityIndicator indicators ; // Object to get quality indicators

    // Logger object and file to store log messages
    logger_      = Configuration.logger_ ;
//    fileHandler_ = new FileHandler("PESA2_main.log");
//    logger_.addHandler(fileHandler_) ;
    
    indicators = null ;
    if (args.length == 1) {
      Object [] params = {"Real"};
      problem = (new ProblemFactory()).getProblem(args[0],params);
    } // if
    else if (args.length == 2 || args.length >= 6) {
      Object [] params = {"Real"};
      problem = (new ProblemFactory()).getProblem(args[0],params);
      indicators = new QualityIndicator(problem, args[1]) ;
    } // if
    else { // Default problem
      problem = new Kursawe("Real", 3); 
      //problem = new Water("Real");
      //problem = new ZDT1("ArrayReal", 1000);
      //problem = new ZDT4("BinaryReal");
      //problem = new WFG1("Real");
      //problem = new DTLZ1("Real");
      //problem = new OKA2("Real") ;
    } // else
    
    algorithm = new PESA2(problem);
    
    // Algorithm parameters 
    algorithm.setInputParameter("populationSize",10);
    algorithm.setInputParameter("archiveSize",100);
//    algorithm.setInputParameter("bisections",5);

    if (problem.getNumberOfObjectives()>=10) {
        algorithm.setInputParameter("bisections",2);
    } else if (problem.getNumberOfObjectives()>=6) {
        algorithm.setInputParameter("bisections",3);
    } else
        algorithm.setInputParameter("bisections",5);
    
    algorithm.setInputParameter("maxEvaluations",25000);



    if (args.length >= 5) {
//        algorithm.setInputParameter("populationSize",Integer.parseInt(args[2]));
        algorithm.setInputParameter("archiveSize",Integer.parseInt(args[2]));
        algorithm.setInputParameter("doCrossover", Boolean.parseBoolean(args[3]));
        algorithm.setInputParameter("doMutation", Boolean.parseBoolean(args[4]));
        algorithm.setInputParameter("epsilonGridWidth", Double.parseDouble(args[5]));
        algorithm.setInputParameter("doOnMPICluster", Boolean.parseBoolean(args[6]));
        algorithm.setInputParameter("maxEvaluations",Integer.parseInt(args[7]));

    } else {
//        algorithm.setInputParameter("populationSize",100);
        algorithm.setInputParameter("archiveSize",100);
        algorithm.setInputParameter("doMutation", true);
        algorithm.setInputParameter("doCrossover", true);
    }
    algorithm.setInputParameter("infoPrinterHowOften", 100);
    algorithm.setInputParameter("infoPrinterSubDir", args[9]);



    
    // Mutation and Crossover for Real codification 
    crossover = CrossoverFactory.getCrossoverOperator("SBXCrossover");                   
    crossover.setParameter("probability",0.9);                   
    crossover.setParameter("distributionIndex",20.0);
    
    mutation = MutationFactory.getMutationOperator("PolynomialMutation");                    
    mutation.setParameter("probability",1.0/problem.getNumberOfVariables());
    mutation.setParameter("distributionIndex",20.0);
    
    // Mutation and Crossover Binary codification
    /*
    crossover = CrossoverFactory.getCrossoverOperator("SinglePointCrossover");                   
    crossover.setParameter("probability",0.9);                   
    mutation = MutationFactory.getMutationOperator("BitFlipMutation");                    
    mutation.setParameter("probability",1.0/80);
    */
    
    // Add the operators to the algorithm
    algorithm.addOperator("crossover",crossover);
    algorithm.addOperator("mutation",mutation);


    algorithm.setInputParameter("indicators", indicators) ;
    algorithm.addOperator("selection", (Selection) SelectionFactory.getSelectionOperator(args[8]));
    
    
    // Execute the Algorithm
    long initTime = System.currentTimeMillis();
    SolutionSet population = algorithm.execute();
    long estimatedTime = System.currentTimeMillis() - initTime;
    System.out.println("Total execution time: "+estimatedTime);
    
    // Result messages 
    logger_.info("Total execution time: "+estimatedTime);
//    logger_.info("Objectives values have been writen to file FUN");
//    population.printObjectivesToFile("FUN");
//    logger_.info("Variables values have been writen to file VAR");
//    population.printVariablesToFile("VAR");
  }//main
} // PESA2_main
