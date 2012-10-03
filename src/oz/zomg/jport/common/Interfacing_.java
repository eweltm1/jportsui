package oz.zomg.jport.common;

/**
 * Name space class.
 * <H3><I><FONT color="#770000">Subset of original source.</FONT></I></H3>
 *
 * @author <SMALL>Copyright 2012 by Stephen Baber
 * &nbsp; <a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/3.0/deed.en_US">
 * <img alt="Creative Commons License" style="border-width:0" src="http://i.creativecommons.org/l/by-nc-nd/3.0/80x15.png" /></a><br />
 * This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/3.0/deed.en_US">
 * Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License</a>.</SMALL>
 */
public class Interfacing_
{
    private Interfacing_() {}


    // ================================================================================
    /**
     * Implementor (that is probably not of type T) promises that it can create an instance of T.
     * The method name .create() makes the code more understandable than just implementing Receivable.
     *
     * @param <T> object is of class type
     */
    static public interface Creatable<T>
    {
        abstract public T create();
    }


    // ================================================================================
    /**
     * Has Total Equivalence == Deep Equals == Very Equal == More Equal Than Others
     *
     * To avoid breaking the contract for Object.equals() and disrupt the collection classes, Implementor
     * tests if two objects are similar enough to be considered equivalent.  If you have to implement this method
     * and you don't know what to do with it, a safe bet is <code> return this.equals( toAnother ); </code>
     *
     * For example .equals() generally makes a quick check against only UUIDs, logical IDs, file path names, etc.
     * (preferably in sync with the fields used to generate  .hashcode() ) whereas .isEquivalent() performs a more
     * thorough check of all fields. In other words x.equals(y)==true but x.isEquivalent(y)==false and not the converse.
     *
     * For making determinations using a looser or a minimum of information, consider implementing Approximatable<T>
     *
     * @param <T>
     */
    static public interface Equatable<T>
    {
        abstract public boolean equals( Object obj ); // used when associating differing objs with java.util.Collections. Ex. ==Track#s
        abstract public boolean isEquivalent( T toAnother ); // used when checking all obj fields
    }


    // ================================================================================
    /**
     * "Shoot" an object at a Targetable instance.
     *
     * @param <T> object is of class type
     */
    static public interface Targetable<T>
    {
        abstract public void target( T obj );
    }


    // ================================================================================
    /**
     * Implementor can transform instances in/from an object out/to instances of another object.
     *
     * @param <I> transformable input instance is of class type
     * @param <O> transformed output instance is of class type
     */
    static public interface Transformable<I,O>
    {
        abstract public O transform( I input );
    }


    // ================================================================================
    /**
     * Implementor promises to clear all Collections and dispose all resources without the drama of .finalize().
     * The end goal here is to prevent stressing the GarbageCollector into becoming Oscar The Grouch with too many
     * non-finalizable circular Object references.
     */
    static public interface Unleakable
    {
        abstract public void unleak();
    }
}
