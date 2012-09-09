package jport.type;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import jport.PortsCatalog;
import jport.PortsConstants;
import jport.PortsConstants.EPortMark;
import jport.PortsConstants.EPortStatus;
import jport.TheApplication;
import jport.common.HttpUtil;
import jport.common.StringsUtil_;


/**
 * Implementation of Port info originating from a PortBuilder
 * instance via the "PortIndex" file.
 * Mostly immutable Port with lazily instantiated,
 * non-recursive external Port dependencies as
 * the entire Port index has to be constructed first.
 *
 * @author sbaber
 */
class BsdPort //... refactor IndexPort?
    implements Portable
{
    /** Guard value. */
    static final Portable BSD_NONE = new BsdPort();

    /** Expected large set size optimization for unique dependencies. */
    static final private Set<Portable> _SCRATCH_PORT_SET = new HashSet<Portable>( 64 );

    static
    {}

    final private String
              ci_name // case-insensitive name
            , name
            , description
            , long_description
            , homepage
            , epoch
            , version
            , revision
            , portdir
            , replaced_by
            , installs_libs
            ;

    final private String[]
              variants
            , subports
            , depends_fetch
            , depends_extract
            , depends_lib
            , depends_build
            , depends_run
            , platforms
            , maintainers
            , license
            , categories
            ;

    /** Mutable because lazily instantiated.  Not recursive, see .getFullDeps() for that. */
    private Portable[] mUniqueDependencies = null;


    /**
     * Constructor for the NONE port, to avoid use of 'null'.
     */
    private BsdPort()
    {
        ci_name           =
        name              =
        description       =
        long_description  =
        homepage          =
        epoch             =
        version           =
        revision          =
        portdir           =
        replaced_by       =
        installs_libs     = "";

        variants          =
        subports          =
        depends_fetch     =
        depends_extract   =
        depends_lib       =
        depends_build     =
        depends_run       =
        platforms         =
        maintainers       =
        license           =
        categories        = StringsUtil_.NO_STRINGS;
    }

    /**
     * Copy constructor.
     *
     * @param cp
     */
    BsdPort( final BsdPort cp )
    {
        ci_name           = cp.ci_name;

        name              = cp.name;
        homepage          = cp.homepage;
        epoch             = cp.epoch;
        version           = cp.version;
        revision          = cp.revision;

        description       = cp.description;
        long_description  = cp.long_description;
        portdir           = cp.portdir;
        replaced_by       = cp.replaced_by;
        installs_libs     = cp.installs_libs;

        variants          = cp.variants;
        subports          = cp.subports;
        depends_fetch     = cp.depends_fetch;
        depends_extract   = cp.depends_extract;
        depends_lib       = cp.depends_lib;
        depends_build     = cp.depends_build;
        depends_run       = cp.depends_run;
        platforms         = cp.platforms;
        maintainers       = cp.maintainers;
        license           = cp.license;
        categories        = cp.categories;

        mUniqueDependencies = cp.mUniqueDependencies;
    }

    /**
     * Construct from builder.
     *
     * @param pb
     */
    BsdPort( final PortBuilder pb )
    {
        ci_name           = pb.name.toLowerCase().intern(); // needed for dependency resolution as some are mis-cased

        name              = pb.name    .intern();
        homepage          = pb.homepage.intern();
        epoch             = pb.epoch   .intern();
        version           = pb.version .intern();
        revision          = pb.revision.intern();

        description       = pb.description;
        long_description  = ( pb.long_description.equals( pb.description ) == false ) ? pb.long_description : pb.description; // short and long need to be different else GC long
        portdir           = pb.portdir;
        replaced_by       = pb.replaced_by;
        installs_libs     = pb.installs_libs;

        variants          = pb.variants;
        subports          = pb.subports;
        depends_fetch     = pb.depends_fetch;
        depends_extract   = pb.depends_extract;
        depends_lib       = pb.depends_lib;
        depends_build     = pb.depends_build;
        depends_run       = pb.depends_run;
        platforms         = pb.platforms;
        categories        = pb.categories;

        maintainers       = ( pb.maintainers.length == 1 && "nomaintainer".equals( pb.maintainers[ 0 ] ) ) ? StringsUtil_.NO_STRINGS : pb.maintainers; // common cased
        license           = ( pb.license.length == 1 && "unknown".equals( pb.license[ 0 ] ) ) ? StringsUtil_.NO_STRINGS : pb.license; // common cased
    }

    @Override public String getName()             { return this.name; }
    @Override public String getShortDescription() { return this.description; }
    @Override public String getLongDescription()  { return this.long_description; }
    @Override public String getVersion()          { return this.version; }
    @Override public String getHomepage()         { return this.homepage; }

    @Override public String[] getCategories()     { return this.categories; }
    @Override public String[] getLicenses()       { return this.license; }
    @Override public String[] getMaintainers()    { return this.maintainers; }
    @Override public String[] getVariants()       { return this.variants; } // possible variants that can be installed

    @Override public String getCaseInsensitiveName()     { return ci_name; }
    @Override public String getPortDirectory()           { return portdir; }

    @Override public String getDomain()
    {
        return HttpUtil.getDomain( this.getHomepage() );
    }

    @Override public boolean isInstalled() { return false; }

    /**
     * The current, actual, on disk, status of the Port.
     * Not the user's "marked" action for the Port CLI.
     * Overridden by CliPort.
     *
     * @param statusEnum
     * @return 'true' if in the bloom filter
     */
    @Override public boolean hasStatus( final EPortStatus statusEnum )
    {
        return statusEnum == EPortStatus.ALL || statusEnum == EPortStatus.UNINSTALLED; // the other stati require that the Port is installed
    }

    @Override public void setStatus( final EPortStatus statusEnum )
    {
        throw new IllegalArgumentException( "Only "+ CliPort.class.getCanonicalName() +" supports requesting status changes" );
    }

    @Deprecated
    private boolean hasAnyStatus( final EPortStatus... stati )
    {
        for( final EPortStatus status : stati )
        {
            if( hasStatus( status ) == true ) return true;
        }
        return false;
    }

    @Override public String getVersionInstalled() { return ""; }

    @Override public String[] getVariantsInstalled() { return StringsUtil_.NO_STRINGS; }

    static private void _addDeps( final PortsCatalog portsCatalog, final String[] depNames )
    {
        for( final String dependency : depNames )
        {
            final Portable port = portsCatalog.parse( dependency );
            if( port != null )
            {   // found it as expected
                _SCRATCH_PORT_SET.add( port );
            }
            else
            {   // * OEM error, these dependencies asked for but not found <- System.err.println( dependency );
                // MPlayer
                // MPlayer
                // py25-pylons
                // py26-h5py
                // py26-pylons
                // py26-pylons
                // py26-h5py
                // py26-h5py
                // py27-h5py
                // py27-pylons
                // py27-h5py
                // py27-h5py
                // py31-django
                // py32-django
                // rb19-fattr
            }
        }
    }

    /**
     *
     *
     * @return non-recursive, unique dependencies
     */
    @Override public Portable[] getDeps()
    {
        if( mUniqueDependencies == null )
        {   // lazy instantiated
            if(    this.depends_lib    .length == 0
                && this.depends_build  .length == 0
                && this.depends_run    .length == 0
                && this.depends_fetch  .length == 0
                && this.depends_extract.length == 0
              )
            {   // no dependencies
                mUniqueDependencies = PortsConstants.NO_PORTS;
            }
            else
            {   // unroll the unique deps add
                final PortsCatalog portsCatalog = TheApplication.INSTANCE.getPortsCatalog();
                _addDeps( portsCatalog, this.depends_lib );
                _addDeps( portsCatalog, this.depends_build );
                _addDeps( portsCatalog, this.depends_run );
                _addDeps( portsCatalog, this.depends_fetch );
                _addDeps( portsCatalog, this.depends_extract );

                mUniqueDependencies = _SCRATCH_PORT_SET.toArray( new BsdPort[ _SCRATCH_PORT_SET.size() ] ); // checked for 0 above
                _SCRATCH_PORT_SET.clear(); // no leak and prepare for next time
                // defer until detail view -> Arrays.sort( mUniqueDependencies );
            }
        }

        return mUniqueDependencies; // is not recursive
    }

    /**
     * has dependencies on
     *
     * @return other ports this port has dependencies of being installed first
     */
    @Override public Portable[] getFullDependencies()
    {
        return TheApplication.INSTANCE.getPortsCatalog().getDeps().getFullDependenciesOf( this );
    }

    /**
     * is dependant of
     *
     * @return other ports that are dependant on this port being installed and activated before them
     */
    @Override public Portable[] getDependants()
    {
        return TheApplication.INSTANCE.getPortsCatalog().getDeps().getDependants( this );
    }

    @Override public boolean hasDependency( final Portable onPort )
    {
        return TheApplication.INSTANCE.getPortsCatalog().getDeps().hasDependency( this, onPort );
    }

    private boolean isADependant( final Portable ofPort )
    {
        return TheApplication.INSTANCE.getPortsCatalog().getDeps().isADependant( this, ofPort );
    }

    /**
     *
     * @return 'true' if no mark has been applied by the user.
     */
    @Override public boolean isUnmarked()
    {
        return TheApplication.INSTANCE.getPortsMarker().isUnmarked( this );
    }

    /**
     * User's desired action.
     *
     * @return 'null' if not marked
     */
    @Override public EPortMark getMark()
    {
        return TheApplication.INSTANCE.getPortsMarker().getMark( this );
    }

    /**
     * Overwrites user's previous value.
     *
     * @param markEnum
     */
    @Override public void setMark( final EPortMark markEnum )
    {
        TheApplication.INSTANCE.getPortsMarker().setMark( this, markEnum );
    }

    /**
     * Remove any user marking from this Port.
     */
    @Override public void unmark()
    {
        TheApplication.INSTANCE.getPortsMarker().unmark( this );
    }

    @Override public long getModificationEpoch()
    {
        return TheApplication.INSTANCE.getPortsCatalog().getModificationEpoch( this );
    }

    /**
     *
     * @return the long description even if the same as the short because sometimes the entire text of the short description does not fit in the table column
     */
    @Override public String provideTipText()
    {
        final String mark = ( isUnmarked() == true )
                ? ""
                : "<FONT color=#444444>"+ getMark() +"\u2192</FONT> "; // dark-gray, right arrow

        final StringBuilder tip = new StringBuilder( "<HTML><CENTER><B>"+ mark + getName() +"</B></CENTER><BR>"  );

        final String[] strings = this.getLongDescription().split( "\\s" ); // break on whitespace

        for( int i = 0; i < strings.length; i++ )
        {
            if( i != 0 && i % 10 == 0 )
            {   // word wrap
                tip.append( "<BR>" );
            }

            tip.append( strings[ i ] ).append( ' ' );
        }

        return tip.toString();
    }

    @Override public Color provideForeColor()
    {   // sequential check logic
        return hasStatus( EPortStatus.UNINSTALLED ) ? Color.DARK_GRAY
             : hasStatus( EPortStatus.OUTDATED )    ? Color.RED
             : hasStatus( EPortStatus.ACTIVE )      ? Color.BLUE
             : hasStatus( EPortStatus.INACTIVE )    ? Color.MAGENTA
             : hasStatus( EPortStatus.INSTALLED )   ? Color.MAGENTA
                                                    : Color.YELLOW.darker(); // ?
    }

    private boolean moreEquals( final BsdPort port )
    {
        if( port == this ) return true;

        return this.ci_name.equals( port.ci_name ) && this.version.equals( port.version );
    }

    @Override final public boolean equals( final Object obj )
    {
        if( obj == this ) return true;

        if( obj instanceof BsdPort )
        {
            final Portable other = (Portable)obj;
            return this.ci_name.equals( other.getCaseInsensitiveName() );
        }
        return false;
    }

    @Override final public int hashCode()
    {
        return ci_name.hashCode();
    }

    @Override final public int compareTo( final Portable another )
    {
        if( another == this ) return 0;

        return ci_name.compareTo( another.getCaseInsensitiveName() );
    }

    @Override final public String toString()
    {
        return this.name;
    }
}
