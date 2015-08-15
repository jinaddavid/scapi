#include <string>
#include <boost/multiprecision/cpp_int.hpp> 

// Using boost::multiprecision:mpz_int - Arbitrary precision integer type.
namespace mp = boost::multiprecision;     // Reduce the typing a bit later...
typedef boost::multiprecision::int1024_t biginteger;


using namespace std;

/**
* This is a marker interface. It allows the generation of a GroupElement at an abstract level without knowing the actual type of Dlog Group.
*
*/
class GroupElementSendableData
{

};

/**
* This is the main interface of the Group element hierarchy.<p>
* We can refer to a group element as a general term OR we can relate to the fact that an element of an elliptic curve
* is a point and an element of a Zp group is a number between 0 and p-1.
*
*/
class GroupElement
{
public:
	/**
	* checks if this element is the identity of the group.
	* @return <code>true</code> if this element is the identity of the group;<p>
	* 		   <code>false</code> otherwise.
	*/
	virtual bool isIdentity() = 0;

	/**
	* This function is used when a group element needs to be sent via a {@link edu.biu.scapi.comm.Channel} or any other means of sending data (including serialization).
	* It retrieves all the data needed to reconstruct this Group Element at a later time and/or in a different VM.
	* It puts all the data in an instance of the relevant class that implements the GroupElementSendableData interface.
	* @return the GroupElementSendableData object
	*/
	virtual GroupElementSendableData * generateSendableData() = 0;	
};

/*
* The GroupParams family holds the necessary parameters for each possible concrete Dlog group. <p>
* Each DlogGroup has different parameters that constitute this group. GroupParams classes hold those parameters.
*/
class GroupParams
{
private:
	static const long serialVersionUID = -2073134974201958294L;

protected:
	biginteger q; //the group order


public:
	/*
	* Returns the group order, which is the number of elements in the group
	* @return the order of the group
	*/
	biginteger getQ() { return q; }
	virtual ~GroupParams() = 0;
};



class DlogGroup
{
public:
	/**
	* Each concrete class implementing this interface returns a string with a meaningful name for this type of Dlog group.
	* For example: "elliptic curve over F2m" or "Zp*"
	* @return the name of the group type
	*/
	virtual string getGroupType() = 0;

	/**
	* The generator g of the group is an element of the group such that, when written multiplicatively, every element of the group is a power of g.
	* @return the generator of this Dlog group
	*/
	virtual GroupElement * getGenerator() = 0;

	/**
	* GroupParams is a structure that holds the actual data that makes this group a specific Dlog group.<p>
	* For example, for a Dlog group over Zp* what defines the group is p.
	*
	* @return the GroupParams of that Dlog group
	*/
	virtual GroupParams * getGroupParams() = 0;

	/**
	*
	* @return the order of this Dlog group
	*/
	virtual biginteger getOrder() = 0;

	/**
	*
	* @return the identity of this Dlog group
	*/
	virtual GroupElement * getIdentity() = 0;

	/**
	* Checks if the given element is a member of this Dlog group
	* @param element possible group element for which to check that it is a member of this group
	* @return <code>true</code> if the given element is a member of this group;<p>
	* 		   <code>false</code> otherwise.
	* @throws IllegalArgumentException
	*/
	virtual bool isMember(const GroupElement& element) = 0;

	/**
	* Checks if the order is a prime number
	* @return <code>true<code> if the order is a prime number; <p>
	* 		   <code>false</code> otherwise.
	*
	*/
	virtual bool isPrimeOrder() = 0;

	/**
	* Checks if the order of this group is greater than 2^numBits
	* @param numBits
	* @return <code>true</code> if the order is greater than 2^numBits;<p>
	* 		   <code>false</code> otherwise.
	*/
	virtual bool isOrderGreaterThan(int numBits) = 0;

	/**
	* Checks if the element set as the generator is indeed the generator of this group.
	* @return <code>true</code> if the generator is valid;<p>
	*         <code>false</code> otherwise.
	*/
	virtual bool isGenerator() = 0;

	/**
	* Checks parameters of this group to see if they conform to the type this group is supposed to be.
	* @return <code>true</code> if valid;<p>
	*  	   <code>false</code> otherwise.
	*/
	virtual bool validateGroup() = 0;

	/**
	* Calculates the inverse of the given GroupElement.
	* @param groupElement to invert
	* @return the inverse element of the given GroupElement
	* @throws IllegalArgumentException
	**/
	virtual GroupElement * getInverse(GroupElement * groupElement) = 0;

	/**
	* Raises the base GroupElement to the exponent. The result is another GroupElement.
	* @param exponent
	* @param base
	* @return the result of the exponentiation
	* @throws IllegalArgumentException
	*/
	virtual GroupElement * exponentiate(const GroupElement& base, biginteger exponent) = 0;

	/**
	* Multiplies two GroupElements
	* @param groupElement1
	* @param groupElement2
	* @return the multiplication result
	* @throws IllegalArgumentException
	*/
	virtual GroupElement * multiplyGroupElements(const GroupElement& groupElement1, const GroupElement& groupElement2) = 0;

	/**
	* Creates a random member of this Dlog group
	* @return the random element
	*/
	virtual GroupElement * createRandomElement() = 0;

	/**
	* Creates a random generator of this Dlog group
	* @return the random generator
	*/
	virtual GroupElement * createRandomGenerator() = 0;

	/**
	* This function allows the generation of a group element by a protocol that holds a Dlog Group but does not know if it is a Zp Dlog Group or an Elliptic Curve Dlog Group.
	* It receives the possible values of a group element and whether to check membership of the group element to the group or not.
	* It may be not necessary to check membership if the source of values is a trusted source (it can be the group itself after some calculation). On the other hand,
	* to work with a generated group element that is not really an element in the group is wrong. It is up to the caller of the function to decide if to check membership or not.
	* If bCheckMembership is false always generate the element. Else, generate it only if the values are correct.
	* @param bCheckMembership
	* @param values
	* @return the generated GroupElement
	* @throws IllegalArgumentException
	*/
	virtual GroupElement * generateElement(bool bCheckMembership, vector<biginteger> values) = 0;


	/**
	* Reconstructs a GroupElement given the GroupElementSendableData data, which might have been received through a Channel open between the party holding this DlogGroup and
	* some other party.
	* @param bCheckMembership whether to check that the data provided can actually reconstruct an element of this DlogGroup. Since this action is expensive it should be used only if necessary.
	* @param data the GroupElementSendableData from which we wish to "reconstruct" an element of this DlogGroup
	* @return the reconstructed GroupElement
	*/
	virtual GroupElement * reconstructElement(bool bCheckMembership, const GroupElementSendableData& data) = 0;

	/**
	* Computes the product of several exponentiations with distinct bases
	* and distinct exponents.
	* Instead of computing each part separately, an optimization is used to
	* compute it simultaneously.
	* @param groupElements
	* @param exponentiations
	* @return the exponentiation result
	*/
	virtual GroupElement * simultaneousMultipleExponentiations(vector<GroupElement> groupElements, vector<biginteger> exponentiations) = 0;

	/**
	* Computes the product of several exponentiations of the same base
	* and distinct exponents.
	* An optimization is used to compute it more quickly by keeping in memory
	* the result of h1, h2, h4,h8,... and using it in the calculation.<p>
	* Note that if we want a one-time exponentiation of h it is preferable to use the basic exponentiation function
	* since there is no point to keep anything in memory if we have no intention to use it.
	* @param base
	* @param exponent
	* @return the exponentiation result
	*/
	virtual GroupElement * exponentiateWithPreComputedValues(const GroupElement& base, biginteger exponent) = 0;

	/**
	* This function cleans up any resources used by exponentiateWithPreComputedValues for the requested base.
	* It is recommended to call it whenever an application does not need to continue calculating exponentiations for this specific base.
	*
	* @param base
	*/
	virtual void endExponentiateWithPreComputedValues(const GroupElement& base) = 0;

	/**
	* This function takes any string of length up to k bytes and encodes it to a Group Element.
	* k can be obtained by calling getMaxLengthOfByteArrayForEncoding() and it is calculated upon construction of this group; it depends on the length in bits of p.<p>
	* The encoding-decoding functionality is not a bijection, that is, it is a 1-1 function but is not onto.
	* Therefore, any string of length in bytes up to k can be encoded to a group element but not every group element can be decoded to a binary string in the group of binary strings of length up to 2^k.<p>
	* Thus, the right way to use this functionality is first to encode a byte array and then to decode it, and not the opposite.
	*
	* @param binaryString the byte array to encode
	* @return the encoded group Element <B> or null </B>if element could not be encoded
	*/
	virtual GroupElement * encodeByteArrayToGroupElement(vector<unsigned char> binaryString) = 0;

	/**
	* This function decodes a group element to a byte array. This function is guaranteed to work properly ONLY if the group element was obtained as a result of
	* encoding a binary string of length in bytes up to k.<p>
	* This is because the encoding-decoding functionality is not a bijection, that is, it is a 1-1 function but is not onto.
	* Therefore, any string of length in bytes up to k can be encoded to a group element but not any group element can be decoded
	* to a binary sting in the group of binary strings of length up to 2^k.
	*
	* @param groupElement the element to decode
	* @return the decoded byte array
	*/
	virtual vector<unsigned char> decodeGroupElementToByteArray(const GroupElement& groupElement) = 0;


	/**
	* This function returns the value <I>k</I> which is the maximum length of a string to be encoded to a Group Element of this group.<p>
	* Any string of length <I>k</I> has a numeric value that is less than (p-1)/2 - 1.
	* <I>k</I> is the maximum length a binary string is allowed to be in order to encode the said binary string to a group element and vice-versa.<p>
	* If a string exceeds the <I>k</I> length it cannot be encoded.
	* @return k the maximum length of a string to be encoded to a Group Element of this group. k can be zero if there is no maximum.
	*/
	virtual int getMaxLengthOfByteArrayForEncoding() = 0;

	/**
	* This function maps a group element of this dlog group to a byte array.<p>
	* This function does not have an inverse function, that is, it is not possible to re-construct the original group element from the resulting byte array.
	* @return a byte array representation of the given group element
	*/
	virtual vector<unsigned char> mapAnyGroupElementToByteArray(const GroupElement& groupElement) = 0;
};
