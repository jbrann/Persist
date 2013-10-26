package org.brann.persist.TestSuite;

public class Content implements java.io.Serializable {
    
    private int value;
    
    public Content() {
        value = 0;
    }
    
    /** Getter for property value.
     * @return Value of property value.
     */
    public int getValue() {
        return value;
    }
    
    /** Setter for property value.
     * @param value New value of property value.
     */
    public void setValue(int value) {
        this.value = value;
    }
    
    public String toString() {
        return Integer.toString(value);
    }
    
}

