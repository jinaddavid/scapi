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
package edu.biu.scapi.circuits.garbledCircuit;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import edu.biu.scapi.circuits.circuit.Gate;
import edu.biu.scapi.circuits.encryption.MultiKeyEncryptionScheme;
import edu.biu.scapi.exceptions.CiphertextTooLongException;
import edu.biu.scapi.exceptions.KeyNotSetException;
import edu.biu.scapi.exceptions.PlaintextTooLongException;
import edu.biu.scapi.exceptions.TweakNotSetException;

/**
 * This is a standard Garbled Gate. By standard we mean that it is not
 * specialized for specific optimizations. Note though that even optimized
 * circuits may make use of {@code StandardGarbledGate}. For example,
 * {@code FreeXORGarbledBooleanCircuit} a circuit that is optimized with the
 * Free XOR technique uses {@code StandardGarbledGate}s for all of its non-XOR
 * gates.
 * 
 * @author Steven Goldfeder
 * 
 */
public class StandardGarbledGate extends AbstractGarbledGate {
  /**
	 * 
	 */
	private static final long serialVersionUID = 6883263530344158239L;
/**
   * The {@code MultiKeyEncryptionScheme} that will be used to garbled and
   * compute this Gate
   */
  MultiKeyEncryptionScheme mes;

  
  /**
   * This is the garbled circuit associated with this gate. We need a reference to the garbled circuit since the garbled table
   * is stored in the circuit and not in the gate. This is done since we would like to send only the garbled tables and not the entire 
   * circuit that contains all the gates and other information that can be retrieved from the related boolean circuit.
   */
  GarbledBooleanSubCircuit gbc;
  /**
   * Constructs a garbled circuit from an ungarbled circuit using the
   * {@code MultiKeyEncryptionScheme} that is passes as a parameter.
   * 
   * @param mes
   *          The encryption scheme used to garbled this gate
   * @param ungarbledGate
   *          the gate to garbled
   * @param allWireValues
   *          this {@code Map} contains both values for each {@code GarbledWire}
   *          that is an input wire to this gate. These values will be used to
   *          create the garbled truth table.
   * @param signalBits
   *          A{@code Map} containing a signal bit for each {@code GarbledWire}
   *          that is used to determine the order of the entries to the garbled
   *          truth table.
   * @throws InvalidKeyException
   * @throws IllegalBlockSizeException
   * @throws KeyNotSetException
   * @throws TweakNotSetException
   * @throws PlaintextTooLongException
   */
  public StandardGarbledGate(GarbledBooleanSubCircuit gbc, Gate ungarbledGate,
      Map<Integer, SecretKey[]> allWireValues, Map<Integer, Integer> signalBits)
      throws InvalidKeyException, IllegalBlockSizeException,
      KeyNotSetException, TweakNotSetException, PlaintextTooLongException {

    this.mes = gbc.getMultiKeyEncryptionScheme();
    this.gbc = gbc;
    
    inputWireLabels = ungarbledGate.getInputWireLabels();
    outputWireLabels = ungarbledGate.getOutputWireLabels();
    gateNumber = ungarbledGate.getGateNumber();
    numberOfInputs = inputWireLabels.length;
    numberOfOutputs = outputWireLabels.length;
    /*
     * The number of rows truth table is 2^(number of inputs)
     */
    int numberOfRows = (int) Math.pow(2, numberOfInputs);
    byte[] truthTable = new byte[numberOfRows * mes.getCipherSize()];
    
    //put the garbled table into the array of tables stored in the circuit. This will be filled later on.
    gbc.getGarbledTables()[gateNumber] = truthTable;

    for (int rowOfTruthTable = 0; rowOfTruthTable < numberOfRows; rowOfTruthTable++) {
      ByteBuffer tweak = ByteBuffer.allocate(16);
      tweak.putInt(gateNumber);
      int permutedPosition = 0;
      SecretKey[] keysToEncryptOn = new SecretKey[numberOfInputs];
      for (int i = 0, j = (int) Math.pow(2, numberOfInputs - 1), reverseIndex = numberOfInputs - 1; i < numberOfInputs; i++, j /= 2, reverseIndex--) {
        /*Truth table inputs are arranged according to binary number values. j is the value that begins as a 1 in the
         * leftmost(most significant bit) of the binary number that is the size of the truth table. Say for example that there are
         * 3 inputs. So the truth table has 3 input columns. j begins as the binary number 100 and we use it to check wehter the leftmost bit in
         * the row of the truth table is set. If it is, that means that the input value is a 1.  Otherwise it's a 0. We then divide j by 2 to obtain the binary
          * number 010 and we use this to determine the value of the inputs in the second column. We then divide by 2 again to obtain the 
         * binary number 001 and use it to determine the value of the inputs in the third column
         */
        
        int input = ((rowOfTruthTable & j) == 0) ? 0 : 1;
        int signalBit = signalBits.get(inputWireLabels[i]);
        permutedPosition += (input ^ signalBit) * (Math.pow(2, reverseIndex));
        keysToEncryptOn[i] = allWireValues.get(inputWireLabels[i])[input];
        /*
         * We add the signalBit that is placed on the end of the wire's value
         * which is given by input XOR signalBit(i.e. the random bit for the
         * wire). Again, to clarify we use the term signal bit to mean both the
         * random but assigned to each wire as well as the bit that is
         * associated with each of the wire's 2 values. The latter value is
         * obtained by XORing the signal bit of the wire with the actual value
         * that the garbled value is encoding. So, for example if the signal bit
         * for the wire is 0. Then the 0-encoded value will have 0 XOR 0 = 0 as
         * its signal bit. The 1-encoded value will have 0 XOR 1 = 1 as its
         * signal bit.
         */
        tweak.putInt(input ^ signalBit);
      }
      mes.setKey(mes.generateMultiKey(keysToEncryptOn));
      mes.setTweak(tweak.array());
      int value = (ungarbledGate.getTruthTable().get(rowOfTruthTable) == true) ? 1: 0;
      
      System.arraycopy( mes.encrypt(allWireValues.get(outputWireLabels[0])[value].getEncoded()) , 0, truthTable, permutedPosition*mes.getCipherSize(), mes.getCipherSize());
      //truthTable[permutedPosition] = mes.encrypt(new ByteArrayPlaintext(
        //  allWireValues.get(outputWireLabels[0])[value] 
          //    .getEncoded()));
    }

  }

  public void compute(Map<Integer, GarbledWire> computedWires)
  throws InvalidKeyException, IllegalBlockSizeException,
  CiphertextTooLongException, KeyNotSetException, TweakNotSetException {
int truthTableIndex = getIndexToDecrypt(computedWires);
/*
 * we regenerate the multiSecretKEy and the tweak. We then reset the tweak
 * and the key to the MultiKeyEncryptionScheme and call its decrypt function
 */
SecretKey[] keysToDecryptOn = new SecretKey[numberOfInputs];
for (int i = 0; i < numberOfInputs; i++) {
  keysToDecryptOn[i] = computedWires.get(inputWireLabels[i])
      .getValueAndSignalBit();
}
mes.setKey(mes.generateMultiKey(keysToDecryptOn));
ByteBuffer tweak = ByteBuffer.allocate(16);
// first we put the gate number in the tweak
tweak.putInt(gateNumber);
// next we put the signal bits of the input wire values into the tweak
for (int i = 0; i < numberOfInputs; i++) {
  tweak.putInt(computedWires.get(inputWireLabels[i]).getSignalBit());
}
mes.setTweak(tweak.array());

byte[] truthTable = gbc.getGarbledTables()[gateNumber];
SecretKey wireValue = new SecretKeySpec(
     mes.decrypt(Arrays.copyOfRange(truthTable, truthTableIndex * mes.getCipherSize(), (truthTableIndex +1)*mes.getCipherSize())),
    "");

for (int i = 0; i < numberOfOutputs; i++) {

  computedWires.put(outputWireLabels[i], new GarbledWire(
      outputWireLabels[i], wireValue));

}
}

/**A helper method that computed which index to decrypt based on the signal bits of the input wires.
* @param computedWires a {@code Map} containing the input wires and their values. We will use it to obtain the 
* signal bits of the values of the input wires in order to determine the correct index to decrypt
* @return the index of the garbled truth table that the input wires' signal bits signal to decrypt
*/
private int getIndexToDecrypt(Map<Integer, GarbledWire> computedWires) {
int truthTableIndex = 0;
for (int i = numberOfInputs - 1, j = 0; j < numberOfInputs; i--, j++) {
  truthTableIndex += computedWires.get(inputWireLabels[i]).getSignalBit()
      * Math.pow(2, j);
}
return truthTableIndex;
}

@Override
public boolean verify(Gate g, Map<Integer, SecretKey[]> allWireValues)
  throws IllegalBlockSizeException, CiphertextTooLongException,
  KeyNotSetException, TweakNotSetException, InvalidKeyException {

/*
 * Step 1: First we test to see that these gate's are labeled with the same
 * integer label. if they're not, then for our purposes they are not
 * identical. The reason that we treat this as unequal is since in a larger
 * circuit corresponding gates must be identically labeled in order for the
 * circuits to be the same.
 */
if (gateNumber != g.getGateNumber()) {
  return false;
}
/*
 * Step 2: we check to ensure that the inputWirelabels and ouputWireLabels
 * are the same
 */
int[] ungarbledInputWireLabels = g.getInputWireLabels();
int[] ungarbledOutputWireLabels = g.getOutputWireLabels();

if (numberOfInputs != ungarbledInputWireLabels.length
    || numberOfOutputs != ungarbledOutputWireLabels.length) {
  return false;
}
for (int i = 0; i < numberOfInputs; i++) {
  if (inputWireLabels[i] != ungarbledInputWireLabels[i]) {
    return false;
  }
}
for (int i = 0; i < numberOfOutputs; i++) {
  if (outputWireLabels[i] != ungarbledOutputWireLabels[i]) {
    return false;
  }
}
/*
 * Step 3. Use allWireValues(i.e. a map that maps each wire to an array that
 * contains its 0-encoding and its 1-encoding) to go through every
 * combination of input wire values and decrypt the corresponding row of the
 * truth table
 * 
 * Step 4. The decrypted values of the truth table should be(at most) 2
 * distinct keys--i.e. a 0-encoding for the output wire and a 1-encoding for
 * the output wire. So, we test whether the arrangement of the garbled truth
 * table is consistent with the ungarbled truth table. Specifically, if the
 * ungarbled truth table is 0001, then we test to ensure that the first,
 * second and third entries of the garbled truth table are identical and
 * that the fourth entry is different. If this is not true, we return false
 * as the two truth tables are not consistent. If this is true, then we add
 * the output wires with the corresponding values to the allWireValues map.
 * Thus, in our example with the 0001 truth table, the garbled value that
 * corresponds to 0(i.e it appears in the first, second and third positions
 * of the truth table) is stored as the 0 value for the output wire. The
 * value corresponding to 1 is stored as the 1 value for the output wire.
 */

BitSet ungarbledTruthTable = g.getTruthTable();
SecretKey outputZeroValue = null;
SecretKey outputOneValue = null;
// The outer for loop goes through each row of the truth table
for (int rowOfTruthTable = 0; rowOfTruthTable < Math.pow(2, numberOfInputs); rowOfTruthTable++) {

  /*
   * permuted position will be the index of the garbled truth table
   * corresponding to rowOfTruthTable
   */
  int permutedPosition = 0;
  ByteBuffer tweak = ByteBuffer.allocate(16);
  tweak.putInt(gateNumber);
  SecretKey[] keysToDecryptOn = new SecretKey[numberOfInputs];
  /*
   * This for loop goes through from left to right the input of the given
   * row of the truth table.
   */
  for (int i = 0, j = (int) Math.pow(2, numberOfInputs - 1), reverseIndex = numberOfInputs - 1; i < numberOfInputs; i++, j /= 2, reverseIndex--) {
    int input = ((rowOfTruthTable & j) == 0) ? 0 : 1;
    SecretKey currentWireValue = allWireValues.get(inputWireLabels[i])[input];
    /*
     * add the current Wire value to the list of keys to decrypt on. These
     * keys will then be used to construct a multikey.
     */
    keysToDecryptOn[i] = currentWireValue;
    /*
     * look up the signal bit on this wire. This is the last bit of its
     * value.
     */
    int signalBit = (currentWireValue.getEncoded()[currentWireValue
        .getEncoded().length - 1] & 1) == 0 ? 0 : 1;
    /*
     * update the permuted position. After this loop finishes, permuted
     * position will be the position on the garbled truth table for this
     * input wire combination. For a better understanding on how this works,
     * see the getIndexToDecrypt method in this class
     */
    permutedPosition += signalBit * Math.pow(2, reverseIndex);
    // add the signal bit of this input wire value to the tweak
    tweak.putInt(signalBit);
  }
  mes.setKey(mes.generateMultiKey(keysToDecryptOn));
  mes.setTweak(tweak.array());
  byte[] truthTable = gbc.getGarbledTables()[gateNumber];
  byte[] pt = mes.decrypt(Arrays.copyOfRange(truthTable, permutedPosition * mes.getCipherSize(), (permutedPosition + 1) *mes.getCipherSize()));
  /*
   * we now check to see that rows of the truth table with the same
   * ungarbled value have the same garbled value as well
   */
  if (ungarbledTruthTable.get(rowOfTruthTable) == true) {// i.e this bit is
                                                         // set
    if (outputOneValue != null) {
      byte[] ptBytes = pt;
      byte[] oneValueBytes = outputOneValue.getEncoded();
      for (int byteArrayIndex = 0; byteArrayIndex < ptBytes.length; byteArrayIndex++) {
        if (ptBytes[byteArrayIndex] != oneValueBytes[byteArrayIndex]) {
          return false;
        }
      }
    } else {
      outputOneValue = new SecretKeySpec(pt, "");
    }
  } else { // i.e if(ungarbledTruthTable.get(rowOfTruthTable)==false)
                                                    //bit is not set
    if (outputZeroValue == null) {
      outputZeroValue = new SecretKeySpec(pt, "");
    } else {
      byte[] zeroValueBytes = outputZeroValue.getEncoded();
      for (int byteArrayIndex = 0; byteArrayIndex < pt.length; byteArrayIndex++) {
        if (pt[byteArrayIndex] != zeroValueBytes[byteArrayIndex]) {
          return false;
        }
      }
    }
  }
}
//we add the output wire to the allWireValues Map
for (int w : outputWireLabels) {
  allWireValues.put(w, new SecretKey[] {outputZeroValue, outputOneValue });
}
return true;
}
}
