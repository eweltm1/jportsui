package jport.common;

/**
 * This class looks stupid but Integer.valueOf() only caches 256 numbers
 * and the profiler indicates much time is being spent making new Integer wrappers.
 * For all possible ints with best performance, could be a Map custom coded to accept
 * the 'int' primitive as the key and SoftReference the Integer value.
 * <H3><I><FONT color="#770000">Subset of original source.</FONT></I></H3>
 *<P>
 * Note: can not extend Integer wrapper as it is 'final'.
 *
 * @author sbaber
 */
public class CachedInteger_
{
    static final public Integer ZERO = Integer.valueOf( 0 ); // someday the JVM might do this for us?
    static final public Integer ONE  = Integer.valueOf( 1 );

    static final private int _MIN_CACHED = -1;
    static final private int _MAX_CACHED = 32768;

    /** There simply is not enough time to perform a hash.get() so an array is employed. */
    static final private Integer[] _INTEGERS = new Integer[ 1 + _MAX_CACHED - _MIN_CACHED ];

    static final private boolean _PROFILE = false;
    static       private int MIN_PROFILE = Integer.MAX_VALUE;
    static       private int MAX_PROFILE = Integer.MIN_VALUE;

    static // initializer block
    {}

    private CachedInteger_() {}

    static public Integer valueOf( final int i )
    {
        if( i == 0 ) return ZERO;

        if( i < _MIN_CACHED || i > _MAX_CACHED )
        {   // cache miss
            if( _PROFILE == true )
            {
                if( i < MIN_PROFILE ) MIN_PROFILE = i;
                if( i > MAX_PROFILE ) MAX_PROFILE = i;
                System.out.println( "No Integer=" + i + "  Min=" + MIN_PROFILE + "  Max=" + MAX_PROFILE );
            }

            return Integer.valueOf( i ); // perhaps some day zero-cost from JVM // access the negative number cache of Integer
        }

        Integer integer = _INTEGERS[ i - _MIN_CACHED ];
        if( integer == null )
        {   // lazy cache hit
            integer = Integer.valueOf( i );
            _INTEGERS[ i - _MIN_CACHED ] = integer;
        }
        return integer;
    }

    /**
     * Copy out a contiguous range.
     *
     * @param from
     * @param to
     * @return will be in reverse order if from > to
     */
    static public Integer[] slice( final int from, final int to )
    {
        final int begin = Math.min( from, to );
        final int end   = Math.max( from, to );
        final int size = 1 + end - begin;
        final Integer[] integers = new Integer[ size ];

        // Can not use System.arraycopy() even if in range of begin >= _MIN_CACHED && end <= _MAX_CACHED
        // because _INTEGERS may contain 'nulls'.
        for( int j = begin, i = 0; i < size; i++, j++ )
        {
            integers[ i ] = CachedInteger_.valueOf( j );
        }

        if( to < from )
        {   // descending
            Util.reverseOrderInPlace( integers );
        }

        return integers;
    }
}
