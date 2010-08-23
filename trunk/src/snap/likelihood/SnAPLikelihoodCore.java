
/*
 * File SnAPLikelihoodCore.java
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

/** Ourisia60 
 20 samples
real	0m39.389s
user	0m41.267s
sys	0m0.308s

 */


import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import snap.NodeData;

public class SnAPLikelihoodCore  {
	boolean m_bReuseCache = false;
	
	public SnAPLikelihoodCore(Node root, Alignment data) {
		SiteProbabilityCalculator.clearCache(root.getNodeCount(), data.getMaxStateCount());
	}


	/**
	 Compute Likelihood of the allele counts
	 
	 @param root  The tree. Uses branch lengths and gamma values stored on this tree.
	 @param u  Mutation rate from red to green
	 @param v Mutation rate from green to red
	 @param sampleSize  Number of samples taken at each species (index by id field of the NodeData)
	 @param redCount  Each entry is a different marker... for each marker the number of red alleles in each species.
	 @param siteProbs  Vector of probabilities (logL) for each site.
	 * @throws Exception 
	 **/
	
	public double computeLogLikelihood(NodeData root, double u, double v, 
			int [] sampleSizes, 
			Alignment data, 
			boolean bUseCache,
			boolean dprint /*= false*/) throws Exception
	{
		LineageCountCalculator.computeCountProbabilities(root,sampleSizes,dprint);
		//dprint = true;
			
			//TODO: Partial subtree updates over all sites.
			
			
			double forwardLogL = 0.0;
			int numPatterns = data.getPatternCount();

			//Temporarily store pattern probabilities... used for numerical checks.
			double [] patternProb = new double[numPatterns];
//			if (m_bReuseCache) {
//				traverse(root);
//			} else {
				SiteProbabilityCalculator.clearCache(root.getNodeCount(), data.getMaxStateCount());
//			}

			for(int id = 0; id < numPatterns - 2; id++) {
			//for(int id=0;id<60;id++) {
				//System.err.print('.');
				if (id>0 && id%100 == 0)
					System.err.print(id + " ");
				int [] thisSite = data.getPattern(id);
				double freq = data.getPatternWeight(id);
				double siteL=0.0;
				try {
					siteL = SiteProbabilityCalculator.computeSiteLikelihood(root, u, v, thisSite, bUseCache, dprint);
				}
				catch (Exception ex) {
					ex.printStackTrace();
					System.exit(1);
				}
				if (siteL==0.0) {
					return -10e100;
				}
				patternProb[id] = siteL;
				//System.err.println(Arrays.toString(thisSite) + " " + siteL);
				forwardLogL+=(double)freq * Math.log(siteL);
			}
			// correction for constant sites
			int [] thisSite = data.getPattern(numPatterns - 2);
			double P0 =  SiteProbabilityCalculator.computeSiteLikelihood(root,u,v,thisSite, false, false);
			thisSite = data.getPattern(numPatterns - 1);
			double P1 =  SiteProbabilityCalculator.computeSiteLikelihood(root,u,v,thisSite, false, false);
			forwardLogL-=(double) data.getSiteCount() * Math.log(1.0 - P0 - P1);
			//System.err.println(numPatterns + " " + forwardLogL);

			return forwardLogL;

//			//Compute site probabilities from pattern probabilities
//			int nSites =   
//					data.getSiteCount();
//
//			// RRB: assume siteProbs is of correct size
//			double [] siteProbs = new double[nSites];
//			for(int i=0;i<nSites;i++) {
//				siteProbs[i] = patternProb[data.getPatternIndex(i)];
//			}
//			
//			//Do some crude numerical checks here.
//			double backwardLogL = 0.0;
//			double EPSILON = 10e-8;
//			for(int site = nSites-1;site>=0;site--) 
//				backwardLogL+=Math.log(siteProbs[site]);
//			if (Math.abs(forwardLogL - backwardLogL)>EPSILON) 
//				System.err.print("Numerical error evaluating likelihood");
//			return (forwardLogL+backwardLogL)/2.0;
	} // computeLogLikelihood
	

	int traverse(Node node) {
		int nState = Tree.IS_CLEAN;
		if (node.isLeaf()) {
			nState |= traverse(node.m_left);
			nState |= traverse(node.m_right);
		}
		if (node.isDirty() != Tree.IS_CLEAN || nState != Tree.IS_CLEAN) {
			SiteProbabilityCalculator.m_cache.clearNode(node.getNr());
			nState |= node.isDirty();
		}
		return nState;
	}
} // class SnAPLikelihoodCore