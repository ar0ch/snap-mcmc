package snap.operators;

import java.util.ArrayList;
import java.util.List;

import snap.likelihood.SnAPTreeLikelihood;
import snap.likelihood.SnapSubstitutionModel;

import beast.core.Description;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.OperatorSchedule;
import beast.core.State;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.core.StateNode;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.distance.Distance;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.sitemodel.SiteModelInterface;
import beast.evolution.tree.Node;
import beast.evolution.tree.TreeInterface;
import beast.util.Randomizer;


@Description("An operator that uses an approximate likelihood and filters out proposales with low acceptance " +
		"based on the approximate likleihood")
public class DelayedAcceptanceOperator extends Operator {
	public Input<Operator> operatorInput = new Input<Operator>("operator", "Operator for proposing moves", Validate.REQUIRED);

	public Input<Double> tuningInput = new Input<Double>("tuning", "tuning parameter for approx likelihood");

	
	public Input<State> stateInput = new Input<State>("state", "state object for which we do proposals", Validate.REQUIRED);

	public Input<Distribution> priorInput = new Input<Distribution>("prior", "prior used when likelihood is approximated", Validate.REQUIRED);
//	public Input<TreeInterface> treeInput = new Input<TreeInterface>("tree", "tree used for approximate likelihood", Validate.REQUIRED);
//	public Input<Alignment> dataInput = new Input<Alignment>("data", "alignment used for approximate likelihood", Validate.REQUIRED);
//    public Input<SiteModelInterface> siteModelInput = new Input<SiteModelInterface>("siteModel", "site model for leafs in the beast.tree", Validate.REQUIRED);
    public Input<SnAPTreeLikelihood> treeLikelihoodInput = new Input<SnAPTreeLikelihood>("treelikelihood", "SNAPP tree likelihood for the tree", Validate.REQUIRED);
	
    
    public boolean useMatLabFormulae = false;
    
    /** probability that sites are variable for the state of the tree before operating on it **/
    double probVariableSites = 1.0;
    
	
	Operator operator;
	Distribution prior = null;
	double priorValue;
	// data and tree used in approximate tree likelihood
	Alignment data = null;
	TreeInterface tree = null;
	
	// TODO: take site model in account in approximation?
	// TODO: take clock model in account in approximation?
	SiteModel.Base siteModel;
	SnapSubstitutionModel substitutionmodel;
	SnAPTreeLikelihood treelikelihood;
	
    State state;
    
    // empirical distance and variance between taxa
    double [][] distance;
    double [][] var;


	// log of constant in approximate tree likelihood
	double K;

	
	public void initAndValidate() {
		operator = operatorInput.get();
		treelikelihood = treeLikelihoodInput.get();
    	siteModel = (SiteModel.Base) treelikelihood.siteModelInput.get();
    	substitutionmodel = ((SnapSubstitutionModel)siteModel.substModelInput.get());
		
    	prior = priorInput.get();
    	tree = treelikelihood.treeInput.get();
    	data = treelikelihood.dataInput.get();
    	state = stateInput.get();

/*
        if (posteriorInput.get() instanceof CompoundDistribution) {
            final CompoundDistribution posterior = (CompoundDistribution) posteriorInput.get();
            final List<Distribution> distrs = posterior.pDistributions.get();
            final int nDistr = distrs.size();
            for (int i = 0; i < nDistr; i++) {
                final Distribution distr = distrs.get(i);
                final String sID = distr.getID();
                if (sID != null && sID.equals("prior")) {
                	prior = distr;
                } else if (sID != null && sID.equals("likelihood")) {
                    if (distr instanceof CompoundDistribution) {
                    	final List<Distribution> distrs2 = ((CompoundDistribution)distr).pDistributions.get();
                    	if (distrs2.size() != 1) {
                    		throw new RuntimeException("DelayedAcceptanceOperator: Expected only one distribution in likelihood");
                    	}
                    	Distribution distr2 = distrs2.get(0);
                    	if (!(distr2 instanceof TreeLikelihood)) {
                    		TreeLikelihood tl = (TreeLikelihood) distr2;
                    		data = tl.dataInput.get();
                    		tree = (Tree) tl.treeInput.get();
                    	}
                    } else {
                		throw new RuntimeException("DelayedAcceptanceOperator: Expected likelihood to be a CompoundDistribution");
                    }
	            } else {
	        		throw new RuntimeException("DelayedAcceptanceOperator: Expected likelihood or prior in posterior and nothing else");
	            }
            }
        } else {
            throw new RuntimeException("DelayedAcceptanceOperator: Expected a CompoundDistribution as posterior input");
        }
*/
        
        if (prior == null) {
        	throw new RuntimeException("DelayedAcceptanceOperator: could not identify prior in posterior input");
        }
        if (data == null || tree == null) {
        	throw new RuntimeException("DelayedAcceptanceOperator: could not identify data or tree in treelikelihood in posterior input");
        }
        
        
        calcDistanceAndVariance();
    }

    private void calcDistanceAndVariance() {
    	// set up distance matrix
    	
		Distance d = new Distance.Base() {
			@Override
			public double pairwiseDistance(int taxon1, int taxon2) {
				double Kxy = 0;
				double d = 0;
				snap.Data _data = (snap.Data) data;
				for (int k = 0; k < _data.getPatternCount(); k++) {
					int [] lineageCounts = _data.getPatternLineagCounts(k);
					int [] sitePattern = _data.getPattern(k);
					double rkx = sitePattern[taxon1];
					double rky = sitePattern[taxon2];
					int nkx = lineageCounts[taxon1];
					int nky = lineageCounts[taxon2];
					double weight = _data.getPatternWeight(k);
					if (weight > 0 && nkx > 0 && nky > 0) {
						if (taxon1 != taxon2) {
							d += weight * (rkx * (nky - rky) + rky * (nkx - rkx))/(nkx * nky);
						} else {
							d += weight * (2.0 * rkx * (nkx - rkx))/(nkx * nkx);
						}
						Kxy += weight;
					}
				}
				return d / Kxy;
			}
		};
		
		Distance v = new Distance.Base() {
			@Override
			public double pairwiseDistance(int taxon1, int taxon2) {
				double Kxy = 0;
				double v = 0;
				snap.Data _data = (snap.Data) data;
				for (int k = 0; k < _data.getPatternCount(); k++) {
					int [] lineageCounts = _data.getPatternLineagCounts(k);
					int [] sitePattern = _data.getPattern(k);
					double rkx = sitePattern[taxon1];
					double rky = sitePattern[taxon2];
					int nkx = lineageCounts[taxon1];
					int nky = lineageCounts[taxon2];
					double weight = _data.getPatternWeight(k);
					if (weight > 0 && nkx > 0 && nky > 0) {
						double v1 = (rkx * rky + (nkx - rkx) * (nky - rky))/(nkx * nky);
						double v2 = (rkx * rkx * rky + (nkx - rkx) * (nkx - rkx) * (nky - rky))/(nkx * nkx * nky);
						if (taxon1 != taxon2) {
							double v3 = (rkx * rky * rky + (nkx - rkx) * (nky - rky) * (nky - rky))/(nkx * nky * nky);
							v += weight * ((1 - nkx - nky) * v1 * v1 + (nkx - 1) * v2 + (nky - 1) * v3 + v1) /(nkx * nky);
						} else {
							v += weight * 2.0 * ((nkx - 1)/(nkx * nkx)) * ((3.0 - 2.0 * nkx) * v1 * v1 + 2.0 * (nkx - 2.0) * v2 + v1);
						}
						Kxy += weight;
					}
				}
				v = v / (Kxy * Kxy);
				//if (true) return 0.00005;
				return v;			
				}
		};
		
		((Distance.Base)d).setPatterns(data);
		((Distance.Base)v).setPatterns(data);
		
		int nrOfTaxa = data.getNrTaxa();
		distance = new double[nrOfTaxa][nrOfTaxa];
		var = new double[nrOfTaxa][nrOfTaxa];
		
		for (int i = 0; i < nrOfTaxa; i++) {
			for (int j = i; j < nrOfTaxa; j++) {
				distance[i][j] = d.pairwiseDistance(i,  j);
				distance[j][i] = distance[i][j];
				var[i][j] = v.pairwiseDistance(i, j);
				var[j][i] = var[i][j];
			} 
		}
		
		
		// calculate log of constant of approximate likelihood
		K = -Math.log(2.0 * Math.PI) * nrOfTaxa * (nrOfTaxa + 1.0)/4.0;
		for (int i = 0; i < nrOfTaxa; i++) {
			for (int j = i; j < nrOfTaxa; j++) {
				K -= Math.sqrt(var[i][j]);
			}
		}
	}

	@Override
    public double proposal()  {

		probVariableSites = treelikelihood.getProbVariableSites();

		
		
    	try {
    		Node oldRoot = tree.getRoot().copy();
        	Double [] oldCoalescenceRate = substitutionmodel.m_pCoalescenceRate.get().getValues();
        	double oldU = substitutionmodel.m_pU.get().getValue();
        	double oldV  = substitutionmodel.m_pV.get().getValue();

        	double oldApproxLogLikelihood = evaluate(oldRoot, oldCoalescenceRate, oldU, oldV);
	    	double oldPrior = priorValue;
	    	
	    	double logHastingsRatio = operator.proposal();
            
			Node newRoot = tree.getRoot();
	    	Double [] newCoalescenceRate = substitutionmodel.m_pCoalescenceRate.get().getValues();
	    	double newU = substitutionmodel.m_pU.get().getValue();
	    	double newV  = substitutionmodel.m_pV.get().getValue();

	    	
	    	// could skip till after checking logHR == -infinity
	    	// but since the proposal can return -infinity in two different cases
	    	// (if slave-operator return -infinity, OR if proposal is rejected)
	    	// that would make it difficult to deal with in the main MCMC loop
	    	// to distinguish between those two cases.
	    	state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            if (logHastingsRatio == Double.NEGATIVE_INFINITY) {
	    		// instant reject
	    		// need to store state, so it is restored properly
	    		return Double.NEGATIVE_INFINITY;
	    	}

	    	double newApproxLogLikelihood = evaluate(newRoot, newCoalescenceRate, newU, newV);
	    	double newPrior = priorValue;

	    	double logAlpha = newApproxLogLikelihood + newPrior - oldApproxLogLikelihood - oldPrior + logHastingsRatio; //CHECK HASTINGS
	        if (logAlpha >= 0 || Randomizer.nextDouble() < Math.exp(logAlpha)) {
	        	// accept
	        	
	        	// TODO: do we need to restore 
	        	//state.restore();
	            // TODO: can we get the state nr?
	            //state.store(-1);

	        	// reset the HR
	        	// no need to worry about HR of slave-operator
	        	// note HR contains priors, that cancel out in MCMC loop
	        	
	        	if (probVariableSites == 1.0) {
		            logHastingsRatio = oldApproxLogLikelihood - newApproxLogLikelihood;
	        	} else {
	        		double operatorLogHastingsRatio = logHastingsRatio;
	        		// we need to correct for non-constant site probability in the newly proposed site
		    		if (logAlpha < 0) {
		    			logHastingsRatio = -logAlpha;
		    		} else {
		    			logHastingsRatio = 0;	
		    		}
//System.err.print(operator.getName() + " " + oldApproxLogLikelihood + " " +  newApproxLogLikelihood + " " + oldPrior + " " + newPrior + " ");			    	
		    		probVariableSites = treelikelihood.getNewProbVariableSites();
			    	oldApproxLogLikelihood = oldPrior + approxLikelihood(oldRoot, oldCoalescenceRate, oldU, oldV);
			    	newApproxLogLikelihood = newPrior + approxLikelihood(newRoot, newCoalescenceRate, newU, newV);
			    	double ratio = oldApproxLogLikelihood - newApproxLogLikelihood - operatorLogHastingsRatio;
			    	if (ratio < 0) {
			    		logHastingsRatio += ratio;
			    	}
//System.err.println(" " + logHastingsRatio + " ");			    	
 			    	return operatorLogHastingsRatio + logHastingsRatio;
	        	}
	        	
	        	
	        } else {
	        	// reject;
	            //state.restore();
	            // TODO: can we get the state nr?
	            //state.store(-1);
	        	return Double.NEGATIVE_INFINITY;
	        }
	    	return logHastingsRatio;
    	} catch (Exception e) {
    		e.printStackTrace();
    		System.err.println("DelayedAcceptanceOperator.proposal " + e.getMessage());
            state.restore();
            // TODO: can we get the state nr?
            state.store(-1);
    		return Double.NEGATIVE_INFINITY;
    	}
    }

	/** calculate approximate posterior **/
	private double evaluate(Node root, Double [] coalescenceRate, double u, double v) throws Exception {
		priorValue = prior.calculateLogP();
		double logP = priorValue;
		logP  += approxLikelihood(root, coalescenceRate, u, v);
		return logP;
	}
	
	
	/**
	 * calculate approximate treelikelihood for tree & data
	 * made public for testing purposes  
	 * */
	public double approxLikelihood(Node root, Double [] coalescenceRate, double u, double v) {
    	// do the real work here
    	double mu[][] = calcMu(root, u, v, coalescenceRate);
    	    	
    	double approxL = 0;
    	int nrOfTaxa = distance.length;
		for (int i = 0; i < nrOfTaxa; i++) {
			for (int j = i+ 1; j < nrOfTaxa; j++) {
				approxL += -0.5 * (distance[i][j] - mu[i][j]) * (distance[i][j] - mu[i][j]) / var[i][j];   
			}
		}

		return K  + approxL;
	}
	
	/** calc approximate distances between taxa **/
    public double[][] calcMu(Node root, double u, double v, Double[] coalescenceRate) {
		// calculate estimates of distance between taxa based on the 
		// tree and other parameters
		
		// 1. calc moment generating function
		double [] M = new double[tree.getNodeCount()];
		calcMomentGeneratingFunction(M, root, u, v, coalescenceRate);
		
		// 2. calc approx distances, store result in u
		double [][] mu = new double[var.length][var.length];
		calcApproxDistance(mu, M, root, u, v);		
		return mu;
	}

	List<Node> calcApproxDistance(double[][] mu, double[] M, Node node,
			double u, double v) {
		int x = node.getNr();
		double t = node.getHeight();
		double pi0 = v/(u+v);
		double pi1 = 1.0 - pi0;
		
		if (node.isLeaf()) {
			// nx = nr of lineages for node x, does not matter whether they are missing
			int nx = data.getStateCounts().get(x);
//			if (useMatLabFormulae) {
//				mu[x][x] = 2.0 * pi0 * pi1 * (1.0 - M[x]) / probVariableSites;
//			} else {
				mu[x][x] = 2.0 * pi0 * pi1 * (1.0 - M[x]) * (1.0 - 1.0/nx)  / probVariableSites;
//			}
			List<Node> list = new ArrayList<Node>();
			list.add(node);
			return list;
		} else {
			List<Node> left = calcApproxDistance(mu, M, node.getLeft(), pi0, pi1);
			List<Node> right = calcApproxDistance(mu, M, node.getRight(), pi0, pi1);
			for (Node lNode : left) {
				for (Node rNode : right) {
					int i = lNode.getNr();
					int j = rNode.getNr();
					mu[i][j] = 2.0 * pi0 * pi1 * (1.0 - Math.exp(-2.0 * (u+v) * t) * M[x]) / probVariableSites;
					mu[j][i] = mu[i][j];
				}
			}
			left.addAll(right);
			return left;
		}
	}

	/**
	 * calculate moment generating function for each node in the tree based on SNAPP parameters, 
	 * and store results in M
	 */
	public void calcMomentGeneratingFunction(double[] M, Node node, double u, double v, Double [] coalescenceRate) {
		if (node.isRoot()) {
			// x = -2(u+v)
			// Mroot = -1/(.5*theta(3)*x - 1); %MGF at root
			// Mroot = -1/(theta(3)*x/2 - 1); %MGF at root
			// Mroot = -1/(x/lambda - 1); %MGF at root
			// Mroot = 1/(1 - x/lambda); %MGF at root
			// Mroot = 1/(1 + 2(u+v)/lambda); %MGF at root
			M[node.getNr()] = 1.0 / (1.0 + 2.0 * (u + v)/coalescenceRate[node.getNr()]);
		} else {
			// M=(exp(T*(x-(2/theta(1))))-1)/(.5*theta(1)*x-1) + M[parent]*exp(T*(x-(2/theta(1))));
			// M=(exp(T*(x-lambda(1)))-1)/(x/lambda(1)-1) + M[parent]*exp(T*(x-lambda(1)));
			// M=(exp(T*(x-lambda))-1)/(x/lambda-1) + exp((x-lambda)* T) * M[parent]
			// M=(exp(tx*(-pi-lambda))-1)/(-pi/lambda-1) + exp((-pi-lambda)* tx) * M[parent]

			double lambda = coalescenceRate[node.getNr()];
			int x = node.getNr();
			double tx = node.getLength();
			double pi = 2.0 * (u + v);
			int parent = node.getParent().getNr();
			if (useMatLabFormulae) {
				M[x]= (Math.exp(tx*(-pi-lambda))-1)/(-pi/lambda-1) + Math.exp((-pi-lambda)* tx) * M[parent];
			} else {		
				//M[x] = (1.0 - Math.exp(-lambda * tx)) * lambda * 
				//		(Math.exp(lambda*tx) -Math.exp(-pi*tx)) / ((lambda + pi) * (Math.exp(lambda * tx) - 1.0));

				M[x] = lambda * (1.0 - Math.exp(-(lambda + pi) * tx)) / (lambda + pi);

				M[x] += Math.exp(-(lambda + pi) * tx) * M[node.getParent().getNr()];
			}	
		}
		if (!node.isLeaf()) {
			calcMomentGeneratingFunction(M, node.getLeft(), u, v, coalescenceRate);
			calcMomentGeneratingFunction(M, node.getRight(), u, v, coalescenceRate);
		}
	}

	// BEAST infrastructure stuff
	@Override
    public void setOperatorSchedule(final OperatorSchedule operatorSchedule) {
        operator.setOperatorSchedule(operatorSchedule);
    }
	
	@Override
	public void accept() {
		// TODO do we want operator tuning? If not, comment out this line as well as in reject() below 
		operator.accept();
	}
	
	@Override
	public void reject() {
		operator.reject();
	}
	
	@Override
	public void optimize(double logAlpha) {
		//operator.optimize(logAlpha);
	}
	
	@Override
	public List<StateNode> listStateNodes() throws Exception {
		return operator.listStateNodes();
	}

	@Override
	public boolean requiresStateInitialisation() {
		return false;
	}
	
	@Override
	public String toString() {
		String s = operator.toString();
		String label = operator.getName();
		int i = label.indexOf("operator");
		if (i >= 0) {
			label = label.substring(0, i) + "DAOprtr" + label.substring(i + 8);
		} else {
			label = this.getClass().getName();
		}
		s = s.replaceAll(operator.getName(), label);
		return s;
	}
	
} // class DelayedAcceptanceOperator