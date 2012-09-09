package jport.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;


/**
 * Name space class.
 *
 * @author sbaber
 */
class CachedUriContent
{
    /** Singleton-esque as not using a real Service Registry */
    static volatile private UriContentCacheable sUriCacheable = null;

    /**
     * @return no caching in case feature is dropped.
     */
    static UriContentCacheable getInstance()
    {
        return new NoCache();
    }

    /**
     * @param isNonVolatile 'true' for on disk persistence
     * @return
     */
    static UriContentCacheable getInstance( final boolean isNonVolatile )
    {
        if( sUriCacheable == null )
        {
            sUriCacheable = ( isNonVolatile == true )
                   ? new NonVolatileCache()
                   : new VolatileCache();
        }

        return sUriCacheable;
    }

    static
    {}

    private CachedUriContent() {}


    // ================================================================================
    /**
     * Similar in spirit to <CODE>Map<K,V></CODE>
     */
    static interface UriContentCacheable
    {
        abstract void clearAll();

        /**
         * @param uri
         * @return 'true' if cached even if 404
         */
        abstract boolean has( URI uri );

        /**
         * @param uri
         * @return content bytes @ URI else 'null' if not available
         */
        abstract UriContent get( URI uri );

        /**
         * @param uri
         * @param contentBytes to put into the cache, can be 'null' for 404
         */
        abstract void put( UriContent uriContent );
    }

    // ================================================================================
    /**
     * Do not use any caching
     */
    static private class NoCache
        implements UriContentCacheable
    {
        @Override public void       clearAll() {}
        @Override public boolean    has( URI uri ) { return false; }
        @Override public UriContent get( URI uri ) { return null; }
        @Override public void       put( UriContent uriContent ) {}
    }


    // ================================================================================
    /**
     * In memory URI cache.  Provides run-time recall.
     */
    static private class VolatileCache
        implements UriContentCacheable
    {
        /** Note: ConcurrentHashMap does not permit 'null' values, HashMap does. */
        final private Map<URI,UriContent>fUriToContentMap = new HashMap<URI,UriContent>();

        @Override synchronized public void clearAll()
        {
            fUriToContentMap.clear();
        }

        @Override synchronized public boolean has( final URI uri )
        {
            return fUriToContentMap.containsKey( uri );
        }

        @Override synchronized public UriContent get( final URI uri )
        {
            return fUriToContentMap.get( uri );
        }

        @Override synchronized public void put( final UriContent uriContent )
        {
            fUriToContentMap.put( uriContent.fUri, uriContent );
        }
    }


    // ================================================================================
    /**
     * Reduces web server bandwidth usage by using home user's disk to persist from memory.
     * Retains in HashMap softly.  When out of memory and content is GC'd, will restore
     * from local media.
     */
    static private class NonVolatileCache
        implements UriContentCacheable
    {
        static final private Thread_Worker _FILE_WORKER_THREAD = new Thread_Worker( NonVolatileCache.class.getSimpleName() );

        static
        {
            _FILE_WORKER_THREAD.start();
        }

        final private Map<URI,Reference<UriContent>>fUriToContentRefMap = new HashMap<URI,Reference<UriContent>>();
        final private boolean fIsNonVolatileAvailable;
        final private File fCacheDirPath;

        /**
         * Default hidden directory.
         */
        NonVolatileCache()
        {
            this( System.getProperty( "user.home" ) + File.separatorChar +".zomg" + File.separatorChar + "uri-cache" );
        }

        /**
         * @param cacheDirectory probably ~/.application-name/uri-cache/ but can be 'null' for none
         */
        private NonVolatileCache( final String cacheDirectory )
        {
            boolean isNonVolatileAvailable = cacheDirectory != null;

            fCacheDirPath = new File( cacheDirectory );
            if( isNonVolatileAvailable == true && fCacheDirPath.exists() == false )
            {   // create the folder if possible
                isNonVolatileAvailable = fCacheDirPath.mkdirs();
            }

            // had write permissions
            fIsNonVolatileAvailable = isNonVolatileAvailable;
        }

        /**
         *
         * @param uri
         * @return compatible with Unix/Windows file names
         */
        static private String sanitizeUri( final URI uri )
        {
            return uri.toString().replace( '/', '.' ).replace( ':', '-' ); // File.separatorChar
        }

        @Override synchronized public void clearAll()
        {
            if( fIsNonVolatileAvailable == true )
            {
                for( final File filePath : fCacheDirPath.listFiles() )
                {
                    filePath.delete();
                }
            }

            fUriToContentRefMap.clear();
        }

        @Override synchronized public boolean has( final URI uri )
        {
            return fUriToContentRefMap.containsKey( uri ) == true || this.get( uri ) != null; // 1st run needs to check on disk also
        }

        @Override synchronized public UriContent get( final URI uri )
        {
            if( fIsNonVolatileAvailable == true )
            {
                if( fUriToContentRefMap.containsKey( uri ) == false || fUriToContentRefMap.get( uri ).get() == null )
                {   // check local disk
                    final File filepath = new File( fCacheDirPath, sanitizeUri( uri ) );
                    if( filepath.exists() == true )
                    {   // read contents
                        try
                        {
                            byte[] contentBytes = Util.retreiveFileBytes( filepath );

                            final UriContent uriContent = ( contentBytes.length != 0 )
                                    ? new UriContent( uri, contentBytes )
                                    : new UriContent( uri ); // 404

                            final Reference<UriContent> uriContentRef = new SoftReference<UriContent>( uriContent );
                            fUriToContentRefMap.put( uri, uriContentRef );
                        }
                        catch( IOException ex )
                        {}
                    }
                }
            }

            final Reference<UriContent> uriContentRef = fUriToContentRefMap.get( uri );
            return ( uriContentRef != null )
                    ? uriContentRef.get() // make available as hard ref
                    : null; // not present
        }

        /**
         * We could make sure the cache file will be completely written to disk by
         * calling through SwingUtilities.invokeLater().
         * Rather perverse as the UI thread is the only one guaranteed to keep the
         * JVM live without having to build a real ServiceProvider architecture.
         *
         * @param uri
         * @param contentBytes
         */
        @Override synchronized public void put( final UriContent uriContent )
        {
            final URI uri = uriContent.fUri; // alias

            if( fUriToContentRefMap.containsKey( uri ) == false )
            {   // new content
                final Reference<UriContent> uriContentRef = new SoftReference<UriContent>( uriContent );
                fUriToContentRefMap.put( uri, uriContentRef );

                if( fIsNonVolatileAvailable == true )
                {
                    _FILE_WORKER_THREAD.offer( new Runnable() // anonymous class
                            {   @Override public void run() // maintains a hard ref to uriContent
                                {   File filePath = new File( fCacheDirPath, sanitizeUri( uri ) );

                                    if( filePath.exists() == true )
                                    {   // tried again
                                        if( filePath.delete() == false ) return;
                                    }

                                    FileOutputStream fileOutputStream = null;
                                    try
                                    {
                                        if( filePath.createNewFile() == true )
                                        {
                                            fileOutputStream = new FileOutputStream( filePath ); // THROWS
                                            if( uriContent.fIs404 == false )
                                            {   // content was not 404
                                                fileOutputStream.write( uriContent.fContentBytes ); // THROWS
                                            }
                                            // else just a zero length file
                                        }
                                    }
                                    catch( IOException ex )
                                    {
                                        ex.printStackTrace();
                                    }
                                    finally
                                    {
                                        Util.close( fileOutputStream );
                                    }
                                }
                            } );
                }
            }
        }
    }
}
