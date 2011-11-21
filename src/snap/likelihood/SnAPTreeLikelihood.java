
/*
 * File SnAPTreeLikelihood.java
 *
 * Copyright (C) 2010 Remco Bouckaert, David Bryant remco@cs.auckland.ac.nz
 *
 * This file is part of SnAP.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * SnAP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  SnAP is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with SnAP; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package snap.likelihood;



import java.util.List;
import java.util.Random;

import beast.app.BeastMCMC;
import beast.evolution.alignment.Alignment;
import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.tree.Tree;

import snap.NodeData;
import snap.likelihood.SnAPLikelihoodCore;


@Description("Implements a tree Likelihood Function for Single Site Sorted-sequences on a tree.") 
@Citation("David Bryant, Remco Bouckaert, Noah Rosenberg. Inferring species trees directly from SNP and AFLP data: full coalescent analysis without those pesky gene trees. arXiv:0910.4193v1. http://arxiv.org/abs/0910.4193")
public class SnAPTreeLikelihood extends TreeLikelihood {
//	public Input<Data> m_pData = new Input<Data>("data", "set of alignments");
//	public Input<Tree> m_pTree = new Input<Tree>("tree", "tree with phylogenetic relations");

	public Input<Boolean> m_bInitFromTree = new Input<Boolean>("initFromTree", "whether to initialize coalescenceRate from starting tree values (if true), or vice versa (if false)");
	public Input<String> m_pPattern = new Input<String>("pattern", "pattern of metadata element associated with this parameter in the tree");

	public Input<Boolean> m_usenNonPolymorphic = new Input<Boolean>("non-polymorphic", "Whether to use non-polymorphic data in the sequences. " +
			"If true, constant-sites in the data will be used as part of the likelihood calculation. " +
			"If false (the default) constant sites will be removed from the sequence data and a normalization factor is " +
			"calculated for the likelihood.", false);
	
	public Input<Boolean> mutationOnlyAtRoot = new Input<Boolean>("mutationOnlyAtRoot", "Conditioning on zero mutations, except at root (default false)", false);
	public Input<Boolean> hasDominantMarkers = new Input<Boolean>("dominant", "indicate that alleles are dominant (default false)", false);
	
	public SnAPTreeLikelihood() throws Exception {
		// suppress some validation rules
		m_pSiteModel.setRule(Validate.OPTIONAL);
	}
	
	/** shadow variable of m_pData input */
	Alignment m_data2;
	/** SampleSizes = #lineages per taxon **/
	int [] m_nSampleSizes;
	/** likelihood core, doing the actual hard work of calculating the likelihood **/
	SnAPLikelihoodCore m_core;
	
	/** some variable for shadowing inputs **/
	boolean m_bUsenNonPolymorphic;
	boolean m_bMutationOnlyAtRoot;
	boolean m_bHasDominantMarkers;
    
	SnapSubstitutionModel m_substitutionmodel;
	
    @Override
    public void initAndValidate() throws Exception {
    	// check that alignment has same taxa as tree
    	if (m_data.get().getNrTaxa() != m_tree.get().getLeafNodeCount()) {
    		throw new Exception("The number of nodes in the tree does not match the number of sequences");
    	}

    	m_bUsenNonPolymorphic = m_usenNonPolymorphic.get();
    	m_bMutationOnlyAtRoot = mutationOnlyAtRoot.get();
    	m_bHasDominantMarkers = hasDominantMarkers.get();
    	
    	m_siteModel = m_pSiteModel.get();
    	
    	Tree tree = m_tree.get();
    	m_substitutionmodel = ((SnapSubstitutionModel)m_pSiteModel.get().m_pSubstModel.get());
    	Input<RealParameter> coalescenceRatenput = m_substitutionmodel.m_pCoalescenceRate;
		
		Double [] values = new Double[tree.getNodeCount()];
		String sCoalescenceRateValues = "";
		if (m_bInitFromTree.get() == true) {
			tree.getMetaData(tree.getRoot(), values, m_pPattern.get());
			for (Double d : values) {
				sCoalescenceRateValues += d + " ";
			}
		} else {
	    	String sValue = coalescenceRatenput.get().m_pValues.get();
	    	// remove start and end spaces
	    	sValue = sValue.replaceAll("^\\s+", "");
	    	sValue = sValue.replaceAll("\\s+$", "");
	    	// split into space-separated bits
	    	String [] sValues = sValue.split("\\s+");
	        for (int i = 0; i < values.length; i++) {
	            values[i] = new Double(sValues[i % sValues.length]);
				sCoalescenceRateValues += values[i] + " ";
	        }
			tree.setMetaData(tree.getRoot(), values, m_pPattern.get());
		}
		RealParameter pCoalescenceRate = coalescenceRatenput.get();
		RealParameter coalescenceRate = new RealParameter();
		coalescenceRate.initByName("value", sCoalescenceRateValues, "upper", pCoalescenceRate.getUpper(), "lower", pCoalescenceRate.getLower(), "dimension", values.length);
		coalescenceRate.setID(pCoalescenceRate.getID());
		coalescenceRatenput.get().assignFrom(coalescenceRate);
	
    	
    	
    	
    	m_data2 = m_data.get();
    	if ( BeastMCMC.m_nThreads == 1) {
    		// single threaded likelihood core
    		m_core = new SnAPLikelihoodCore(m_tree.get().getRoot(), m_data.get());
    	} else {
    		// multi-threaded likelihood core
    		m_core = new SnAPLikelihoodCoreT(m_tree.get().getRoot(), m_data.get());
    	}
    	Integer [] nSampleSizes = m_data2.getStateCounts().toArray(new Integer[0]);
    	m_nSampleSizes = new int[nSampleSizes.length];
    	for (int i = 0; i < nSampleSizes.length; i++) {
    		m_nSampleSizes[i] = nSampleSizes[i];
    	}
    	if (!(m_tree.get().getRoot() instanceof NodeData)) {
    		throw new Exception("Tree has no nodes of the wront type. NodeData expected, but found " + 
    				m_tree.get().getRoot().getClass().getName());
    	}

    
    }

    /**
     * Calculate the log likelihood of the current state.
     *
     * @return the log likelihood.
     */
    @Override
    public double calculateLogP() {
    	try {
    		// get current tree
	    	NodeData root = (NodeData) m_tree.get().getRoot();
	    	Double [] coalescenceRate = m_substitutionmodel.m_pCoalescenceRate.get().getValues();
	    	// assing gamma values to tree
//	    	if (m_pGamma.get().somethingIsDirty()) {
//	    		// sync gammas in parameter with gammas in tree, if necessary
//	    		m_pGamma.get().prepare();
//	    	}
	    	
	    	double u = m_substitutionmodel.m_pU.get().getValue();
	    	double v  = m_substitutionmodel.m_pV.get().getValue();
			boolean useCache = true;
			boolean dprint = false;
			
			
			double [] fCategoryRates = m_siteModel.getCategoryRates(null);
			double [] fCategoryProportions = m_siteModel.getCategoryProportions(null);
			double [][] patternProbs = new double[m_siteModel.getCategoryCount()][];
			int nCategories = m_siteModel.getCategoryCount();
			
			// calculate pattern probabilities for all categories
			for (int iCategory = 0; iCategory < nCategories; iCategory++) {
				patternProbs[iCategory] = m_core.computeLogLikelihood(root, 
						u * fCategoryRates[iCategory], 
						v * fCategoryRates[iCategory], 
		    			m_nSampleSizes, 
		    			m_data2,
		    			coalescenceRate,
		    			m_bMutationOnlyAtRoot,
		    			useCache,
		    			dprint /*= false*/);
			}
			
			// amalgamate site probabilities over categories
			int numPatterns = m_data2.getPatternCount();
			double [] fProbs = new double[numPatterns];
			for (int i = 0; i < nCategories; i++) {
				double[] patternProb = patternProbs[i]; 
				for(int id = 0; id < numPatterns; id++) {
					fProbs[id] += patternProb[id] * fCategoryProportions[i];
				}
			}
			// claculate log prob
			logP = 0;
			for(int id = 0; id < numPatterns - (m_bUsenNonPolymorphic ? 0 : 2); id++) {
				double freq = m_data2.getPatternWeight(id);
				double siteL = fProbs[id];
				if (siteL==0.0) {
					logP = -10e100;
					break;
				}
				logP += (double)freq * Math.log(siteL);
			}
			// correction for constant sites
			if (!m_bUsenNonPolymorphic) {
				double P0 =  fProbs[numPatterns - 2];
				double P1 =  fProbs[numPatterns - 1];
				logP -= (double) m_data2.getSiteCount() * Math.log(1.0 - P0 - P1);
			}				
			
			
			
//			logP = m_core.computeLogLikelihood(root, u , v, 
//	    			m_nSampleSizes, 
//	    			m_data2,
//	    			coalescenceRate,
//	    			fCategoryRates, fCategoryProportions,
//	    			useCache,
//	    			m_bUsenNonPolymorphic,
//	    			dprint /*= false*/);
			return logP;
    	} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
    } // calculateLogLikelihood

    /** CalculationNode methods **/
	@Override
	public void store() {
        storedLogP = logP;
    	m_core.m_bReuseCache = true;
    	// DO NOT CALL super.store, since the super class TreeLikelihood has nothing to store
    	//super.store();
    }

	@Override
    public void restore() {
        logP = storedLogP;
    	m_core.m_bReuseCache = false;
    	// DO NOT CALL super.restore, since the super class TreeLikelihood has nothing to store
    	//super.restore();
    }

	
	@Override public List<String> getArguments() {return null;}
	@Override public List<String> getConditions() {return null;}
	@Override public void sample(State state, Random random) {};
} // class SSSTreeLikelihood
