/**
 * SMPSO.java
 * 
 * @author Juan J. Durillo
 * @author Antonio J. Nebro
 * @version 1.0
 */
package jmetal.metaheuristics.smpso;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jmetal.base.*;
import jmetal.util.archive.CrowdingArchive;
import jmetal.base.operator.mutation.*;
import jmetal.base.operator.comparator.*;
import jmetal.base.Algorithm;
import jmetal.qualityIndicator.Hypervolume;
import jmetal.util.*;
import jmetal.util.wrapper.XReal;

import java.util.Comparator;
import java.util.Vector;
import jmetal.qualityIndicator.QualityIndicator;

public class SMPSO extends Algorithm {

  /**
   * Stores the problem to solve
   */
  private Problem problem_;
  /**
   * Stores the number of particles_ used
   */
  private int particlesSize_;
  /**
   * Stores the maximum size for the archive
   */
  private int archiveSize_;
  /**
   * Stores the maximum number of iteration_
   */
  private int maxIterations_;
  /**
   * Stores the current number of iteration_
   */
  private int iteration_;
  /**
   * Stores the particles
   */
  private SolutionSet union;
  /**
   * Stores the best_ solutions founds so far for each particles
   */
  private Solution[] best_;
  /**
   * Stores the leaders_
   */
  private CrowdingArchive population;
  /**
   * Stores the speed_ of each particle
   */
  private double[][] speed_;
  /**
   * Stores a comparator for checking dominance
   */
  private Comparator dominance_;
  /**
   * Stores a comparator for crowding checking
   */
  private Comparator crowdingDistanceComparator_;
  /**
   * Stores a <code>Distance</code> object
   */
  private Distance distance_;
  /**
   * Stores a operator for non uniform mutations
   */
  private Operator polynomialMutation_;

  QualityIndicator indicators_; // QualityIndicator object

  double r1Max_;
  double r1Min_;
  double r2Max_;
  double r2Min_;
  double C1Max_;
  double C1Min_;
  double C2Max_;
  double C2Min_;
  double WMax_;
  double WMin_;
  double ChVel1_;
  double ChVel2_;

  /** 
   * Constructor
   * @param problem Problem to solve
   */
  public SMPSO(Problem problem) {
    problem_ = problem;

    r1Max_ = 1.0;
    r1Min_ = 0.0;
    r2Max_ = 1.0;
    r2Min_ = 0.0;
    C1Max_ = 2.5;
    C1Min_ = 1.5;
    C2Max_ = 2.5;
    C2Min_ = 1.5;
    WMax_ = 0.1;
    WMin_ = 0.1;
    ChVel1_ = -1;
    ChVel2_ = -1;
  } // Constructor

  public SMPSO(Problem problem,
    Vector<Double> variables,
    String trueParetoFront) throws FileNotFoundException {
    problem_ = problem;

    r1Max_ = variables.get(0);
    r1Min_ = variables.get(1);
    r2Max_ = variables.get(2);
    r2Min_ = variables.get(3);
    C1Max_ = variables.get(4);
    C1Min_ = variables.get(5);
    C2Max_ = variables.get(6);
    C2Min_ = variables.get(7);
    WMax_ = variables.get(8);
    WMin_ = variables.get(9);
    ChVel1_ = variables.get(10);
    ChVel2_ = variables.get(11);

    hy_ = new Hypervolume();
    jmetal.qualityIndicator.util.MetricsUtil mu = new jmetal.qualityIndicator.util.MetricsUtil();
    trueFront_ = mu.readNonDominatedSolutionSet(trueParetoFront);
    trueHypervolume_ = hy_.hypervolume(trueFront_.writeObjectivesToMatrix(),
      trueFront_.writeObjectivesToMatrix(),
      problem_.getNumberOfObjectives());

  } // SMPSO
  private double trueHypervolume_;
  private Hypervolume hy_;
  private SolutionSet trueFront_;
  private double deltaMax_[];
  private double deltaMin_[];
  boolean success_;

  /** 
   * Constructor
   * @param problem Problem to solve
   */
  public SMPSO(Problem problem, String trueParetoFront) throws FileNotFoundException {
    problem_ = problem;
    hy_ = new Hypervolume();
    jmetal.qualityIndicator.util.MetricsUtil mu = new jmetal.qualityIndicator.util.MetricsUtil();
    trueFront_ = mu.readNonDominatedSolutionSet(trueParetoFront);
    trueHypervolume_ = hy_.hypervolume(trueFront_.writeObjectivesToMatrix(),
      trueFront_.writeObjectivesToMatrix(),
      problem_.getNumberOfObjectives());

    // Default configuration
    r1Max_ = 1.0;
    r1Min_ = 0.0;
    r2Max_ = 1.0;
    r2Min_ = 0.0;
    C1Max_ = 2.5;
    C1Min_ = 1.5;
    C2Max_ = 2.5;
    C2Min_ = 1.5;
    WMax_ = 0.1;
    WMin_ = 0.1;
    ChVel1_ = -1;
    ChVel2_ = -1;
  } // Constructor

  
  int evaluations;
  
  /**
   * Initialize all parameter of the algorithm
   */
  public void initParams() {
    particlesSize_ = ((Integer) getInputParameter("swarmSize")).intValue();
    archiveSize_ = ((Integer) getInputParameter("archiveSize")).intValue();
    maxIterations_ = ((Integer) getInputParameter("maxIterations")).intValue();

    indicators_ = (QualityIndicator) getInputParameter("indicators");

    polynomialMutation_ = operators_.get("mutation") ; 

    iteration_ = 0 ;

    evaluations = 0;
    
    success_ = false;

//    particles_ = new SolutionSet(particlesSize_);
    union = new SolutionSet(particlesSize_);

    best_ = new Solution[particlesSize_];
//    leaders_ = new CrowdingArchive(archiveSize_, problem_.getNumberOfObjectives());
    population = new CrowdingArchive(archiveSize_, problem_.getNumberOfObjectives());

    // Create comparators for dominance and crowding distance
    dominance_ = new DominanceComparator();
    crowdingDistanceComparator_ = new CrowdingDistanceComparator();
    distance_ = new Distance();

    // Create the speed_ vector
    speed_ = new double[particlesSize_][problem_.getNumberOfVariables()];


    deltaMax_ = new double[problem_.getNumberOfVariables()];
    deltaMin_ = new double[problem_.getNumberOfVariables()];
    for (int i = 0; i < problem_.getNumberOfVariables(); i++) {
      deltaMax_[i] = (problem_.getUpperLimit(i) -
        problem_.getLowerLimit(i)) / 2.0;
      deltaMin_[i] = -deltaMax_[i];
    } // for
  } // initParams 

  // Adaptive inertia 
  private double inertiaWeight(int iter, int miter, double wma, double wmin) {
    return wma; // - (((wma-wmin)*(double)iter)/(double)miter);
  } // inertiaWeight

  // constriction coefficient (M. Clerc)
  private double constrictionCoefficient(double c1, double c2) {
    double rho = c1 + c2;
    //rho = 1.0 ;
    if (rho <= 4) {
      return 1.0;
    } else {
      return 2 / (2 - rho - Math.sqrt(Math.pow(rho, 2.0) - 4.0 * rho));
    }
  } // constrictionCoefficient


  // velocity bounds
  private double velocityConstriction(double v, double[] deltaMax,
                                      double[] deltaMin, int variableIndex,
                                      int particleIndex) throws IOException {


    double result;

    double dmax = deltaMax[variableIndex];
    double dmin = deltaMin[variableIndex];

    result = v;

    if (v > dmax) {
      result = dmax;
    }

    if (v < dmin) {
      result = dmin;
    }

    return result;
  } // velocityConstriction

  /**
   * Update the speed of each particle
   * @throws JMException 
   */
  private void computeSpeed(int iter, int miter) throws JMException, IOException {
    double r1, r2, W, C1, C2;
    double wmax, wmin, deltaMax, deltaMin;
    XReal bestGlobal;

    for (int i = 0; i < particlesSize_; i++) {
    	XReal particle = new XReal(union.get(i)) ;
    	XReal bestParticle = new XReal(best_[i]) ;

      //Select a global best_ for calculate the speed of particle i, bestGlobal
      Solution one, two;
      int pos1 = PseudoRandom.randInt(0, population.size() - 1);
      int pos2 = PseudoRandom.randInt(0, population.size() - 1);
      one = population.get(pos1);
      two = population.get(pos2);

      if (crowdingDistanceComparator_.compare(one, two) < 1) {
        bestGlobal = new XReal(one);
      } else {
        bestGlobal = new XReal(two);
      //Params for velocity equation
      }
      r1 = PseudoRandom.randDouble(r1Min_, r1Max_);
      r2 = PseudoRandom.randDouble(r2Min_, r2Max_);
      C1 = PseudoRandom.randDouble(C1Min_, C1Max_);
      C2 = PseudoRandom.randDouble(C2Min_, C2Max_);
      W = PseudoRandom.randDouble(WMin_, WMax_);
      //
      wmax = WMax_;
      wmin = WMin_;

      for (int var = 0; var < particle.getNumberOfDecisionVariables(); var++) {
        //Computing the velocity of this particle 
        speed_[i][var] = velocityConstriction(constrictionCoefficient(C1, C2) *
          (inertiaWeight(iter, miter, wmax, wmin) *
          speed_[i][var] +
          C1 * r1 * (bestParticle.getValue(var) -
          particle.getValue(var)) +
          C2 * r2 * (bestGlobal.getValue(var) -
          particle.getValue(var))), deltaMax_, //[var],
          deltaMin_, //[var], 
          var,
          i);
      }
    }
  } // computeSpeed

  /**
   * Update the position of each particle
   * @throws JMException 
   */
  private void computeNewPositions() throws JMException {
    for (int i = 0; i < particlesSize_; i++) {
    	//Variable[] particle = particles_.get(i).getDecisionVariables();
    	XReal particle = new XReal(union.get(i)) ;
      //particle.move(speed_[i]);
      for (int var = 0; var < particle.getNumberOfDecisionVariables(); var++) {
      	particle.setValue(var, particle.getValue(var) +  speed_[i][var]) ;
      	
        if (particle.getValue(var) < problem_.getLowerLimit(var)) {
          particle.setValue(var, problem_.getLowerLimit(var));
          speed_[i][var] = speed_[i][var] * ChVel1_; //    
        }
        if (particle.getValue(var) > problem_.getUpperLimit(var)) {
          particle.setValue(var, problem_.getUpperLimit(var));
          speed_[i][var] = speed_[i][var] * ChVel2_; //   
        }
      }
    }
  } // computeNewPositions

  /**
   * Apply a mutation operator to some particles in the swarm
   * @throws JMException 
   */
  private void mopsoMutation(int actualIteration, int totalIterations) throws JMException {
    for (int i = 0; i < union.size(); i++) {
      if ( (i % 6) == 0)
        polynomialMutation_.execute(union.get(i)) ;
      //if (i % 3 == 0) { //particles_ mutated with a non-uniform mutation %3
      //  nonUniformMutation_.execute(particles_.get(i));
      //} else if (i % 3 == 1) { //particles_ mutated with a uniform mutation operator
      //  uniformMutation_.execute(particles_.get(i));
      //} else //particles_ without mutation
      //;
    }
  } // mopsoMutation

  /**   
   * Runs of the SMPSO algorithm.
   * @return a <code>SolutionSet</code> that is a set of non dominated solutions
   * as a result of the algorithm execution  
   * @throws JMException 
   */
  public SolutionSet execute() throws JMException, ClassNotFoundException {
    initParams();

    success_ = false;
    //->Step 1 (and 3) Create the initial population and evaluate
    for (int i = 0; i < particlesSize_; i++) {
      Solution particle = new Solution(problem_);
      problem_.evaluate(particle);
      problem_.evaluateConstraints(particle);
      union.add(particle);
      evaluations++;
    }

    //-> Step2. Initialize the speed_ of each particle to 0
    for (int i = 0; i < particlesSize_; i++) {
      for (int j = 0; j < problem_.getNumberOfVariables(); j++) {
        speed_[i][j] = 0.0;
      }
    }


    // Step4 and 5   
    for (int i = 0; i < union.size(); i++) {
      Solution particle = new Solution(union.get(i));
      population.add(particle);
    }

    //-> Step 6. Initialize the memory of each particle
    for (int i = 0; i < union.size(); i++) {
      Solution particle = new Solution(union.get(i));
      best_[i] = particle;
    }

    //Crowding the leaders_
    distance_.crowdingDistanceAssignment(population, problem_.getNumberOfObjectives());

    
    
    
    int maxEvaluations = ((Integer) getInputParameter("maxEvaluations")).intValue();
    QualityIndicator indicators = (QualityIndicator) getInputParameter("indicators");

    boolean doCrossover = ((Boolean)getInputParameter("doCrossover")).booleanValue();
    boolean doMutation = ((Boolean)getInputParameter("doMutation")).booleanValue();
    int infoPrinterHowOften;
    if (getInputParameter("infoPrinterHowOften")==null) infoPrinterHowOften=1000;
      else infoPrinterHowOften = ((Integer)getInputParameter("infoPrinterHowOften")).intValue();
    boolean doOnMPICluster;
    if (getInputParameter("doOnMPICluster")==null) doOnMPICluster = false;
        else doOnMPICluster = ((Boolean) getInputParameter("doOnMPICluster")).booleanValue();
    String infoPrinterSubDir = (String)getInputParameter("infoPrinterSubDir");
    
    
    
    
    
    
    System.out.println("initial: population.size()="+population.size());
    SolutionSet populationTmp = (SolutionSet) population;
    System.out.println(indicators.paretoFrontFile);
    System.out.println("initial: eps(population)="+indicators.getEpsilon(populationTmp));
    
    boolean analysisWithTime = !true;
    String ifile = indicators.paretoFrontFile;
    QualityIndicator indicatorsTemp = (analysisWithTime?new QualityIndicator(problem_,ifile.replace("1000.", "100000.")):null);
    
    
    
    
    
    //-> Step 7. Iterations ..        
    //while (iteration_ < maxIterations_) {
    while (evaluations < maxEvaluations) {
        
        
        
        
        
        
        
        
        if (analysisWithTime) {
//        if (analysisWithTime && evaluations%5000==0) {
            int current10exp = (int)Math.floor(Math.log10(evaluations));
            int remainder = evaluations % (int)Math.pow(10, current10exp);
            if (remainder == 0 || (evaluations%5000==0&&evaluations<=100000))
                System.out.println(
                        "analysisWithTime:"+this.getClass().getSimpleName()+":"+problem_.getClass().getSimpleName()+" "+evaluations +
                        ",eps("+this.getClass().getSimpleName()+")="+indicatorsTemp.getEpsilon(population) +
                        ",hyp("+this.getClass().getSimpleName()+")="+indicatorsTemp.getHypervolume(population) + 
                        ",gd("+this.getClass().getSimpleName()+")="+indicatorsTemp.getGD(population)+
                        ",igd("+this.getClass().getSimpleName()+")="+indicatorsTemp.getIGD(population)+
                        ",spread("+this.getClass().getSimpleName()+")"+indicatorsTemp.getSpread(population)+
                        ",genspread("+this.getClass().getSimpleName()+")="+indicatorsTemp.getGeneralizedSpread(population)
                        );
            }
        
        
        
        
        
        
        if (infoPrinter==null) 
                if (doOnMPICluster) {
                    infoPrinter = new InfoPrinter(this, problem_, infoPrinterSubDir);
                } else {
                    infoPrinter = new InfoPrinter(this, problem_, infoPrinterSubDir, infoPrinterHowOften, infoPrinterHowOften);
                }
        if (doOnMPICluster) {
            if (union == null) {
                infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, false);
                infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, false);
            } else {
                infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, false);
                infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, false);
            }
        } else {
            if (union == null) {
                infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, false, false);
                infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, false, false);
            } else {
                infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, false, false);
                infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, false, false);
            }
        }
        if (evaluations>=maxEvaluations) {
            if (doOnMPICluster) {
                if (union == null) {
                    infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, true);
                    infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, true);
                } else {
                    infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, true);
                    infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, true);
                }
            }
            break;
        }
        
        
        
        
        
        
        
        
        
        
        
        
      try {
        //Compute the speed_
        computeSpeed(iteration_, maxIterations_);
      } catch (IOException ex) {
        Logger.getLogger(SMPSO.class.getName()).log(Level.SEVERE, null, ex);
      }

      //Compute the new positions for the particles_            
      computeNewPositions();

      //Mutate the particles_          
      mopsoMutation(iteration_, maxIterations_);

      //Evaluate the new particles_ in new positions
      for (int i = 0; i < union.size(); i++) {
        Solution particle = union.get(i);
        problem_.evaluate(particle);
        problem_.evaluateConstraints(particle);
        evaluations++;
      }

      //Actualize the archive          
      for (int i = 0; i < union.size(); i++) {
        Solution particle = new Solution(union.get(i));
        population.add(particle);
      }

      //Actualize the memory of this particle
      for (int i = 0; i < union.size(); i++) {
        int flag = dominance_.compare(union.get(i), best_[i]);
        if (flag != 1) { // the new particle is best_ than the older remeber        
          Solution particle = new Solution(union.get(i));
          //this.best_.reemplace(i,particle);
          best_[i] = particle;
        }
      }

      //Crowding the leaders_
      distance_.crowdingDistanceAssignment(population,
        problem_.getNumberOfObjectives());
      iteration_++;
    }
    
    
    
    
    
    
    
    
    if (doOnMPICluster) {
        if (union == null) {
            infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, true);
            infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, population, indicators, true, true);
        } else {
            infoPrinter.printLotsOfValuesToFile(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, true);
            infoPrinter.printLotsOfValues(this, problem_, operators_, inputParameters_, evaluations, population, union, indicators, true, true);
        }
    }


    // Return the first non-dominated front
    Ranking ranking = new Ranking(population);
    SolutionSet populationTemp = ranking.getSubfront(0);
if (analysisWithTime && evaluations%5000==0 || evaluations==archiveSize_) {
//if (analysisWithTime && evaluations%5000==0) {
                System.out.println(
                        indicatorsTemp.getEpsilon(populationTemp) +
                        ","+indicatorsTemp.getHypervolume(populationTemp) + 
                        ","+indicatorsTemp.getGD(populationTemp)+
                        ","+indicatorsTemp.getIGD(populationTemp)+
                        ","+indicatorsTemp.getSpread(populationTemp)+
                        ","+indicatorsTemp.getGeneralizedSpread(populationTemp)
//                        "analysisWithTime:"+this.getClass().getSimpleName()+":"+problem_.getClass().getSimpleName()+" "+evaluations +
//                        ",eps("+this.getClass().getSimpleName()+")="+indicatorsTemp.getEpsilon(population) +
//                        ",hyp("+this.getClass().getSimpleName()+")="+indicatorsTemp.getHypervolume(population) + 
//                        ",gd("+this.getClass().getSimpleName()+")="+indicatorsTemp.getGD(population)+
//                        ",igd("+this.getClass().getSimpleName()+")="+indicatorsTemp.getIGD(population)+
//                        ",spread("+this.getClass().getSimpleName()+")"+indicatorsTemp.getSpread(population)+
//                        ",genspread("+this.getClass().getSimpleName()+")="+indicatorsTemp.getGeneralizedSpread(population)
                        );
            }
        return populationTemp;
    
  } // execute

  /** 
   * Gets the leaders of the SMPSO algorithm
   */
  public SolutionSet getLeader() {
    return population;
  }  // getLeader   
  
  
  
  
  public static void main(String[] args) {
        try {
            if (args.length==0) {
//            NSGAII_main.main(new String[]{"DTLZ1", "originalParetoFronts\\DTLZ1.2D.pf"});
//            NSGAII_main.main(new String[]{"Water", "originalParetoFronts\\DTLZ1.2D.pf"});
//            NSGAII_main.main(new String[]{"ZDT4","originalParetoFronts\\ZDT4.pf","10","true","true"});
//            NSGAII_main.main(new String[]{"DTLZ7", "nopf", "10", "true", "true","","true"});
//            NSGAII_main.main(new String[]{"Water", "originalParetoFronts\\Water.pf", "10", "true", "true","","true"});
//              BFWN_main.main(new String[]{"WFG1","originalParetoFronts\\WFG1.2D.pf", "10", "true", "true", "0.001", "true","1500","RandomSelection"});
//            NSGAII_main.main(new String[]{"LZ09_F5", "originalParetoFronts\\LZ09_F5.pf", "25", "true", "true", "0.01", "false","1000000","BinaryTournament","foo"});   //problem, Pareto front, pop size, doCrossover, doMutation
            SMPSO_main.main(new String[]{"DTLZ3_4D", "originalParetoFronts\\DTLZ2.4D.1000.pf", "100", "true", "true", "0.01", "false","1000000","BinaryTournament","foo"});   //problem, Pareto front, pop size, doCrossover, doMutation
//            SMPSO_main.main(new String[]{"ZDT2", "originalParetoFronts\\ZDT2.pf", "100", "true", "true", "0.01", "true","100000","BinaryTournament","foo"});   //problem, Pareto front, pop size, doCrossover, doMutation
//            NSGAII_main.main(new String[]{"DTLZ1", "originalParetoFronts\\DTLZ1.3D.pf", "10", "true", "true", "0.001", "true","1500","RandomSelection",""});
//            NSGAII_main.main(new String[]{"WFG1", "originalParetoFronts\\WFG1.2D.pf", "10", "true", "true", "0.001", "true","1500","RandomSelection",""});
//            NSGAII_main.main(new String[]{"Kursawe","originalParetoFronts\\Kursawe.pf"});
            } else if (args.length==7) {
                // in case it is called via the commandline with parameters...
                SMPSO_main.main(new String[]{
                    args[0], // problem
                    "originalParetoFronts/"+args[1], // pf-file, without the directory
                    args[2], // mu
                    "true",  // doXO
                    "true",  // doMUT
                    args[5], // epsilonGridWidth
                    "true",  // do outputs for runs on MPI cluster
                    args[3], // max evaluations
                    args[4], // selection strategy
                    args[6]  // subDirectory name for infoprinter
                });
            } else System.out.println("unsuitable number of parameters. EXIT.");
        } catch (Exception ex) {
            Logger.getLogger(SMPSO.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
} // SMPSO