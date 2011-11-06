package edu.biu.scapi.primitives.dlog.cryptopp;

import java.math.BigInteger;

import edu.biu.scapi.exceptions.UnInitializedException;
import edu.biu.scapi.primitives.dlog.DlogGroupAbs;
import edu.biu.scapi.primitives.dlog.DlogZp;
import edu.biu.scapi.primitives.dlog.GroupElement;
import edu.biu.scapi.primitives.dlog.ZpElement;
import edu.biu.scapi.primitives.dlog.groupParams.ZpGroupParams;

/**This class implements a Dlog group over Zp* utilizing Crypto++'s implementation.<p>
 * It uses JNI technology to call Crypto++'s native code.
 * @author Cryptography and Computer Security Research Group Department of Computer Science Bar-Ilan University (Moriya Farbstein)
 */
public class CryptoPpDlogZp extends DlogGroupAbs implements DlogZp{

	private long PointerToGroup = 0; //pointer to the native group object
	
	/* native functions for the Dlog functionality*/
	private native long createDlogZp(byte[] p, long element);
	private native long inverseElement(long group, long element);
	private native long exponentiateElement(long group, long element, byte[] exponent);
	private native long multiplyElements(long group, long element1, long element2);
	private native void deleteDlogZp(long group);
	private native boolean validateZpGroup(long group);
	private native boolean validateZpGenerator(long group);
	private native boolean validateZpElement(long group, long element);
	
	/**
	 * Initializes the CryptoPP implementation of Dlog over Zp* with the given groupParams
	 * @param groupParams - contains the group parameters
	 */
	public void init(ZpGroupParams groupParams){
			
		//set the inner parameters
		this.groupParams = groupParams;
		
		generator = new ZpElementCryptoPp(groupParams.getXg(), groupParams.getP());
		//create CryptoPP Dlog group
		PointerToGroup = createDlogZp(groupParams.getP().toByteArray(), ((ZpElementCryptoPp) generator).getPointerToElement());
		isInitialized = true;
	}
		
	/**
	 * @return the type of the group - Zp*
	 */
	public String getGroupType(){
		return "Zp*";
	}
	
	/**
	 * Check if the given element is member of this Dlog group
	 * @param element 
	 * @return true if the given element is member of that group. false, otherwise.
	 * @throws UnInitializedException 
	 * @throws IllegalArgumentException
	 */
	public boolean isMember(GroupElement element) throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		//check if element is ZpElementCryptoPp
		if (!(element instanceof ZpElementCryptoPp)){
			throw new IllegalArgumentException("element type doesn't match the group type");
		}
		/*BigInteger elementVal = ((ZpElement) element).getElementValue();
		BigInteger q = ((ZpGroupParams) groupParams).getQ();
		//check if the element is in the appropriate range
		if ((elementVal.compareTo(BigInteger.ZERO)>0) && (elementVal.compareTo(q)<0))
			return true;*/
		return validateZpElement(PointerToGroup, ((ZpElementCryptoPp) element).getPointerToElement());
		
	}

	/**
	 * Checks if the given generator is indeed the generator of the group
	 * @return true, is the generator is valid, false otherwise.
	 * @throws UnInitializedException 
	 */
	public boolean isGenerator() throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return validateZpGenerator(PointerToGroup);
	}

	/**
	 * Checks if the parameters of the group are correct.
	 * @return true if valid, false otherwise.
	 * @throws UnInitializedException 
	 */
	public boolean validateGroup() throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return validateZpGroup(PointerToGroup);
	}

	/**
	 * Calculates the inverse of the given GroupElement
	 * @param groupElement to inverse
	 * @return the inverse element of the given GroupElement
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement getInverse(GroupElement groupElement) throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		if (groupElement instanceof ZpElementCryptoPp){
			//call to native inverse function
			long invertVal = inverseElement(PointerToGroup, ((ZpElementCryptoPp) groupElement).getPointerToElement());
			
			//build a ZpElementCryptoPp element from the result value
			ZpElementCryptoPp inverseElement = new ZpElementCryptoPp(invertVal);
			
			return inverseElement;
			
		}else throw new IllegalArgumentException("element type doesn't match the group type");
	}

	/**
	 * Raises the base GroupElement to the exponent. The result is another GroupElement.
	 * @param exponent
	 * @param base 
	 * @return the result of the exponentiation
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement exponentiate(BigInteger exponent, GroupElement base) throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		if (base instanceof ZpElementCryptoPp){
			//call to native exponentiate function
			long exponentiateVal = exponentiateElement(PointerToGroup, ((ZpElementCryptoPp) base).getPointerToElement(), exponent.toByteArray());
			
			//build a ZpElementCryptoPp element from the result value
			ZpElementCryptoPp exponentiateElement = new ZpElementCryptoPp(exponentiateVal);
			
			return exponentiateElement;
			
		}else throw new IllegalArgumentException("element type doesn't match the group type");
	}

	/**
	 * Multiplies two GroupElements
	 * @param groupElement1
	 * @param groupElement2
	 * @return the multiplication result
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement multiplyGroupElements(GroupElement groupElement1,
			GroupElement groupElement2) throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		if ((groupElement1 instanceof ZpElementCryptoPp) && (groupElement2 instanceof ZpElementCryptoPp)){
			//call to native multiply function
			long mulVal = multiplyElements(PointerToGroup, ((ZpElementCryptoPp) groupElement1).getPointerToElement(), 
										  ((ZpElementCryptoPp) groupElement2).getPointerToElement());
			
			//build a ZpElementCryptoPp element from the result value
			ZpElementCryptoPp mulElement = new ZpElementCryptoPp(mulVal);
			
			return mulElement;
			
		}else throw new IllegalArgumentException("element type doesn't match the group type");
	}

	/**
	 * Creates a random member of this Dlog group
	 * @return the random element
	 * @throws UnInitializedException 
	 */
	public GroupElement getRandomElement() throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return new ZpElementCryptoPp(((ZpGroupParams) groupParams).getP());
	}
	
	/**
	 * Creates a Zp element with the given parameter
	 * @return the created element
	 * @throws UnInitializedException 
	 */
	public ZpElement getElement(BigInteger x) throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return new ZpElementCryptoPp(x, ((ZpGroupParams) groupParams).getP());
	}
	
	/**
	 * delete the related Dlog group object
	 */
	protected void finalize() throws Throwable {
		
		//delete from the dll the dynamic allocation of the Integer.
		deleteDlogZp(PointerToGroup);
		
		super.finalize();
	}
	
	//upload CryptoPP library
	 static {
	        System.loadLibrary("JavaInterface");
	 }

}
