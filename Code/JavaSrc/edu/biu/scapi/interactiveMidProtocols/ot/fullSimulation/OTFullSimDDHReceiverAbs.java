/**
* %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
* 
* Copyright (c) 2012 - SCAPI (http://crypto.biu.ac.il/scapi)
* This file is part of the SCAPI project.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
* to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
* and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
* FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
* 
* We request that any publication and/or code referring to and/or based on SCAPI contain an appropriate citation to SCAPI, including a reference to
* http://crypto.biu.ac.il/SCAPI.
* 
* SCAPI uses Crypto++, Miracl, NTL and Bouncy Castle. Please see these projects for any further licensing issues.
* %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
* 
*/
package edu.biu.scapi.interactiveMidProtocols.ot.fullSimulation;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.util.BigIntegers;

import edu.biu.scapi.comm.Channel;
import edu.biu.scapi.exceptions.CheatAttemptException;
import edu.biu.scapi.exceptions.FactoriesException;
import edu.biu.scapi.exceptions.InvalidDlogGroupException;
import edu.biu.scapi.exceptions.SecurityLevelException;
import edu.biu.scapi.generals.ScapiDefaultConfiguration;
import edu.biu.scapi.interactiveMidProtocols.SigmaProtocol.dh.SigmaDHProverComputation;
import edu.biu.scapi.interactiveMidProtocols.SigmaProtocol.dh.SigmaDHProverInput;
import edu.biu.scapi.interactiveMidProtocols.ot.OTRBasicInput;
import edu.biu.scapi.interactiveMidProtocols.ot.OTRInput;
import edu.biu.scapi.interactiveMidProtocols.ot.OTRGroupElementPairMsg;
import edu.biu.scapi.interactiveMidProtocols.ot.OTROutput;
import edu.biu.scapi.interactiveMidProtocols.ot.OTReceiver;
import edu.biu.scapi.interactiveMidProtocols.ot.OTSMsg;
import edu.biu.scapi.interactiveMidProtocols.zeroKnowledge.ZKPOKFromSigmaCommitPedersenProver;
import edu.biu.scapi.primitives.dlog.DlogGroup;
import edu.biu.scapi.primitives.dlog.GroupElement;
import edu.biu.scapi.securityLevel.DDH;
import edu.biu.scapi.tools.Factories.DlogGroupFactory;

/**
 * Abstract class for Oblivious transfer based on the DDH assumption that achieves full simulation receiver.
 * This implementation can also be used as batch OT that achieves full simulation. In batch oblivious transfer, 
 * the parties run an initialization phase and then can carry out concrete OTs later whenever they have new inputs and wish to carry out an OT. <p>
 * 
 * OT with one sided simulation has two modes: one is on ByteArray and the second is on GroupElement.
 * The different is in the input and output types and the way to process them. 
 * In spite that, there is a common behavior for both modes which this class is implementing.
 * 
 * @author Cryptography and Computer Security Research Group Department of Computer Science Bar-Ilan University (Moriya Farbstein)
 *
 */
abstract class OTFullSimDDHReceiverAbs implements OTReceiver{

	/*	
	 	This class runs the following protocol:
		 	IF NOT VALID_PARAMS(G,q,g0)
			    REPORT ERROR and HALT
			SAMPLE random values y, alpha0 <- {0, . . . , q-1} 
			SET alpha1 = alpha0 + 1 
			COMPUTE 
			1.	g1 = (g0)^y
			2.	h0 = (g0)^(alpha0)
			3.	h1 = (g1)^(alpha1)
			SEND (g1,h0,h1) to S
			Run the prover in ZKPOK_FROM_SIGMA with Sigma protocol SIGMA_DH. Use common input (g0,g1,h0,h1/g1) and private input alpha0.
			
			Transfer Phase (with inputs x0,x1)
			SAMPLE a random value r <- {0, . . . , q-1} 
			COMPUTE
			4.	g = (gSigma)^r
			5.	h = (hSigma)^r
			SEND (g,h) to S
			WAIT for messages (u0,c0) and (u1,c1) from S
			In ByteArray scenario:
			IF  NOT
			�	u0, u1 in G, AND
			�	c0, c1 are binary strings of the same length
			      REPORT ERROR
			OUTPUT  xSigma = cSigma XOR KDF(|cSigma|,(uSigma)^r)
			In GroupElement scenario:
			IF  NOT
			�	u0, u1, c0, c1 in G
			      REPORT ERROR
			OUTPUT  xSigma = cSigma * (uSigma)^(-r)

	*/	
	
	protected DlogGroup dlog;
	private SecureRandom random;
	private ZKPOKFromSigmaCommitPedersenProver zkProver;
	private BigInteger qMinusOne; 
	
	//Pre process values required for calculations:
	private BigInteger y, alpha0, alpha1;
	private GroupElement g1, h0, h1;
	
	/**
	 * Constructor that gets the channel and chooses default values of DlogGroup and SecureRandom.
	 * @param channel
	 * @throws ClassNotFoundException 
	 * @throws CheatAttemptException 
	 * @throws IOException 
	 */
	OTFullSimDDHReceiverAbs(Channel channel) throws IOException, CheatAttemptException, ClassNotFoundException{
		//Read the default DlogGroup name from a configuration file.
		String dlogName = ScapiDefaultConfiguration.getInstance().getProperty("DDHDlogGroup");
		DlogGroup dlog = null;
		try {
			//Create the default DlogGroup by the factory.
			dlog = DlogGroupFactory.getInstance().getObject(dlogName);
		} catch (FactoriesException e1) {
			// Should not occur since the dlog name in the configuration file is valid.
		}
		
		try {
			doConstruct(channel, dlog, new SecureRandom());
		} catch (SecurityLevelException e1) {
			// Should not occur since the dlog in the configuration file is as secure as needed.
		} catch (InvalidDlogGroupException e) {
			// Should not occur since the dlog in the configuration file is valid.
		}
	}
	
	/**
	 * Constructor that sets the given channel, dlogGroup and random.
	 * @param channel
	 * @param dlog must be DDH secure.
	 * @param random
	 * @throws SecurityLevelException if the given dlog is not DDH secure
	 * @throws InvalidDlogGroupException if the given DlogGroup is not valid.
	 * @throws ClassNotFoundException 
	 * @throws CheatAttemptException 
	 * @throws IOException 
	 */
	OTFullSimDDHReceiverAbs(Channel channel, DlogGroup dlog, SecureRandom random) throws SecurityLevelException, InvalidDlogGroupException, IOException, CheatAttemptException, ClassNotFoundException{
		
		doConstruct(channel, dlog, random);
	}
	
	/**
	 * Sets the given members.
	 * Runs the following line from the protocol:
	 * "IF NOT VALID_PARAMS(G,q,g)
	 *   		REPORT ERROR and HALT".
	 * @param channel
	 * @param dlog must be DDH secure.
	 * @param random
	 * @throws SecurityLevelException if the given dlog is not DDH secure
	 * @throws InvalidDlogGroupException if the given DlogGroup is not valid.
	 * @throws ClassNotFoundException 
	 * @throws CheatAttemptException 
	 * @throws IOException 
	 */
	private void doConstruct(Channel channel, DlogGroup dlog, SecureRandom random) throws SecurityLevelException, InvalidDlogGroupException, IOException, CheatAttemptException, ClassNotFoundException {
		//The underlying dlog group must be DDH secure.
		if (!(dlog instanceof DDH)){
			throw new SecurityLevelException("DlogGroup should have DDH security level");
		}
		//Check that the given dlog is valid.
		// In Zp case, the check is done by Crypto++ library.
		//In elliptic curves case, by default SCAPI uploads a file with NIST recommended curves, 
		//and in this case we assume the parameters are always correct and the validateGroup function always return true.
		//It is also possible to upload a user-defined configuration file. In this case,
		//it is the user's responsibility to check the validity of the parameters by override the implementation of this function.
		if(!dlog.validateGroup())
			throw new InvalidDlogGroupException();
		
		this.dlog = dlog;
		this.random = random;
		qMinusOne =  dlog.getOrder().subtract(BigInteger.ONE);
		
		// Some OT protocols have a pre-process stage before the transfer. 
		// Usually, pre process is done once at the beginning of the protocol and will not be executed later, 
		// and then the transfer function could be called multiple times.
		// We implement the preprocess stage at construction time. 
		// A protocol that needs to call preprocess after the construction time, should create a new instance.
		preProcess(channel);
		
	}
	
	/**
	 * Runs the part of the protocol where the receiver input is not yet necessary:
	 * 	"SAMPLE random values y, alpha0 <- {0, . . . , q-1} 
	 *	SET alpha1 = alpha0 + 1 
	 *	COMPUTE 
	 *    1. g1 = (g0)^y
	 *	  2. h0 = (g0)^(alpha0)
	 *	  3. h1 = (g1)^(alpha1)
	 *	SEND (g1,h0,h1) to S
	 *  Run the prover in ZKPOK_FROM_SIGMA with Sigma protocol SIGMA_DH. Use common input (g0,g1,h0,h1/g1) and private input alpha0."
	 * @param channel
	 * @throws ClassNotFoundException 
	 * @throws CheatAttemptException 
	 * @throws IOException 
	 */
	private void preProcess(Channel channel) throws IOException, CheatAttemptException, ClassNotFoundException{
		
		//Sample random values.
		sampleRandomValues();
		
		//Set alpha1 = alpha0 + 1 
		alpha1 = alpha0.add(BigInteger.ONE);
		
		//Calculate tuple elements
		OTFullSimDDHReceiverMsg tuple = computeFirstTuple();
				
		//Send tuple to sender.
		sendTupleToSender(channel, tuple);
		
		//read the default statistical parameter used in sigma protocols from a configuration file.
		String statisticalParameter = ScapiDefaultConfiguration.getInstance().getProperty("StatisticalParameter");
		int t = Integer.parseInt(statisticalParameter);
		
		//Creates the underlying ZKPOK. 
		zkProver = new ZKPOKFromSigmaCommitPedersenProver(channel, new SigmaDHProverComputation(dlog, t, random));
		
		//Run the prover in ZKPOK_FROM_SIGMA with Sigma protocol SIGMA_DH.
		runZKPOK();
	}

	/**
	 * 
	 * Run the following part of the protocol:
	 * Transfer Phase (with inputs x0,x1) <p>
	 *		SAMPLE a random value r <- {0, . . . , q-1} <p>
	 *		COMPUTE<p>
	 *		4.	g = (gSigma)^r<p>
	 *		5.	h = (hSigma)^r<p>
	 *		SEND (g,h) to S<p>
	 *		WAIT for messages (u0,c0) and (u1,c1) from S<p>
	 *		In ByteArray scenario:<p>
	 *		IF  NOT<p>
	 *		�	u0, u1 in G, AND<p>
	 *		�	c0, c1 are binary strings of the same length<p>
	 *		      REPORT ERROR<p>
	 *		OUTPUT  xSigma = cSigma XOR KDF(|cSigma|,(uSigma)^r)<p>
	 *		In GroupElement scenario:<p>
	 *		IF  NOT<p>
	 *		�	u0, u1, c0, c1 in G<p>
	 *		      REPORT ERROR<p>
	 *		OUTPUT  xSigma = cSigma * (uSigma)^(-r)<p>
	 * The transfer stage of OT protocol which can be called several times in parallel.
	 * In order to enable the parallel calls, each transfer call should use a different channel to send and receive messages.
	 * This way the parallel executions of the function will not block each other.
	 * The parameters given in the input must match the DlogGroup member of this class, which given in the constructor.
	 * @param channel
	 * @param input MUST be OTRBasicInput.
	 * @return OTROutput, the output of the protocol.
	 * @throws CheatAttemptException if there was a cheat attempt during the execution of the protocol.
	 * @throws IOException if the send or receive functions failed.
	 * @throws ClassNotFoundException if the receive failed.
	 */
	public OTROutput transfer(Channel channel, OTRInput input) throws IOException, ClassNotFoundException, CheatAttemptException {
		//check if the input is valid.
		//If input is not instance of OTRBasicInput, throw Exception.
		if (!(input instanceof OTRBasicInput)){
			throw new IllegalArgumentException("input should contain sigma.");
		}
		
		byte sigma = ((OTRBasicInput) input).getSigma();
		
		//The given sigma should be 0 or 1.
		if ((sigma != 0) && (sigma!= 1)){
			throw new IllegalArgumentException("Sigma should be 0 or 1");
		}
		
		//Sample a random value r <- {0, . . . , q-1} 
		BigInteger r = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, random);
		
		//Compute tuple (g,h) for sender.
		OTRGroupElementPairMsg a = computeSecondTuple(sigma, r);
		
		//Send tuple to sender.
		sendTupleToSender(channel, a);
		
		//Wait for message from sender.
		OTSMsg message = waitForMessageFromSender(channel);
		
		//Compute the final calculations to get xSigma.
		return checkMessgeAndComputeX(sigma, r, message);
	}

	/**
	 * Runs the following line from the protocol:
	 * "SAMPLE random values y, alpha0 <- {0, . . . , q-1} ". 
	 */
	private void sampleRandomValues() {
		//Sample random values.
		
		y = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, random);
		alpha0 = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, random);
		
	}
	
	/**
	 * Runs the following lines from the protocol:
	 * "COMPUTE 
			1.	g1 = (g0)^y
			2.	h0 = (g0)^(alpha0)
			3.	h1 = (g1)^(alpha1)".
	 * These values are necessary to the message tuple.
	 */
	private OTFullSimDDHReceiverMsg computeFirstTuple() {
		GroupElement g0 = dlog.getGenerator();
		
		g1 = dlog.exponentiate(g0, y);
		h0 = dlog.exponentiate(g0, alpha0);
		h1 = dlog.exponentiate(g1, alpha1);
		
		return new OTFullSimDDHReceiverMsg(g1.generateSendableData(), h0.generateSendableData(), h1.generateSendableData());
	}
	
	/**
	 * Runs the following lines from the protocol:
	 * "COMPUTE 
	 * 4.	g = (gSigma)^r
	 * 5.	h = (hSigma)^r"
	 * @param sigma input of the protocol
	 * @param r random value sampled in the protocol
	 * @return OTRFullSimMessage contains the tuple (g,h).
	 */
	private OTRGroupElementPairMsg computeSecondTuple(byte sigma, BigInteger r) {
		GroupElement g, h;
		
		if (sigma == 0){
			g = dlog.exponentiate(dlog.getGenerator(), r);
			h = dlog.exponentiate(h0, r);
		}
		else {
			g = dlog.exponentiate(g1, r);
			h = dlog.exponentiate(h1, r);
		}
		
		return new OTRGroupElementPairMsg(g.generateSendableData(), h.generateSendableData());
	}
	
	/**
	 * Runs the following line from the protocol:
	 * "SEND tuple to S"
	 * @param channel
	 * @param a the tuple to send to the sender.
	 * @throws IOException 
	 */
	private void sendTupleToSender(Channel channel, Serializable a) throws IOException {
		try {
			channel.send(a);
		} catch (IOException e) {
			throw new IOException("failed to send the message. The thrown message is: " + e.getMessage());
		}
		
	}
	
	/**
	 * Runs the following lines from the protocol:
	 * "Run the prover in ZKPOK_FROM_SIGMA with Sigma protocol SIGMA_DH. 
	 * Use common input (g0,g1,h0,h1/g1) and private input alpha0."
	 * @throws IOException
	 * @throws CheatAttemptException
	 * @throws ClassNotFoundException
	 */
	private void runZKPOK() throws IOException, CheatAttemptException, ClassNotFoundException {
		
		GroupElement g1Inv = dlog.getInverse(g1);
		GroupElement h1DivG1 = dlog.multiplyGroupElements(h1, g1Inv);
		
		zkProver.prove(new SigmaDHProverInput(g1, h0, h1DivG1, alpha0));
	}
	
	/**
	 * Runs the following line from the protocol:
	 * "WAIT for message pairs (w0, c0) and (w1, c1)  from S"
	 * @param channel
	 * @return OTSMessage contains (w0, c0, w1, c1)
	 * @throws IOException if failed to receive.
	 * @throws ClassNotFoundException
	 */
	private OTSMsg waitForMessageFromSender(Channel channel) throws IOException, ClassNotFoundException {
		Serializable message = null;
		try {
			message =  channel.receive();
		} catch (IOException e) {
			throw new IOException("failed to receive message. The thrown message is: " + e.getMessage());
		}
		if (!(message instanceof OTSMsg)){
			throw new IllegalArgumentException("the given message should be an instance of OTSMessage");
		}
		return (OTSMsg) message;
	}
	
	/**
	 * Runs the following lines from the protocol:
	 * "In ByteArray scenario:
	 *		IF  NOT 
	 *			1. w0, w1 in the DlogGroup, AND
	 *			2. c0, c1 are binary strings of the same length
	 *		   REPORT ERROR
	 *		OUTPUT  xSigma = cSigma XOR KDF(|cSigma|,(uSigma)^r)
	 *	In GroupElement scenario:
	 *		IF  NOT 
	 *			1. w0, w1, c0, c1 in the DlogGroup
	 *		   REPORT ERROR
	 *	OUTPUT  xSigma = cSigma * (uSigma)^(-r)"
	 * @param sigma input of the protocol
	 * @param r random value sampled in the protocol
	 * @param message received from the sender
	 * @return OTROutput contains xSigma
	 * @throws CheatAttemptException 
	 */
	protected abstract OTROutput checkMessgeAndComputeX(byte sigma, BigInteger r, OTSMsg message) throws CheatAttemptException;


}
