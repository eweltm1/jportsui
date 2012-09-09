package jport.common;

import ca.mb.javajeff.ico.BadIcoResException;
import ca.mb.javajeff.ico.Ico;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * Handle the weird special casing for Web based images.
 *
 * @author sbaber
 */
public class ImageUtil
{
    private ImageUtil() {}

    //ENHANCE
    /**
     * Special case for Java's broken ".ICO" handling and the web's sloppy adherence to standards.
     *
     * @param resourceName used to check file extension when MIME type info is lost, ".ico", ".png", ".gif", etc.
     * @param bytes content @ URI
     * @return 'null' if no image
     */
    static Image parseImage( final String resourceName, final byte[] bytes )
    {
        if( bytes == null ) throw new NullPointerException();

        if( bytes.length == 0 ) return null;

        if( resourceName.toLowerCase().endsWith( ".ico" ) == false )
        {   // Java can make a good show of JPEG, GIF, PNG, etc.
            return Toolkit.getDefaultToolkit().createImage( bytes );
        }
        else
        {   // special cased, AWT tends to fail with transparent ".ICO".  Bug not completely fixed as of JDK7.07.
            final ByteArrayInputStream bais = new ByteArrayInputStream( bytes ); // wraps a read marker around a byte[]
            try
            {   // generally a Microsoft .ICO image
                final Ico ico = new Ico( bais ); // THROWS BadIcoResException

                Image largestImage = null;
                int largestPerimeter = 0;
                for( int i = 0; i < ico.getNumImages(); i++ )
                {   // find the largest in the series
                    final Image image = ico.getImage( i );
                    final int perimeter = image.getHeight( null ) + image.getWidth( null );
                    if( perimeter > largestPerimeter )
                    {
                        largestPerimeter = perimeter;
                        largestImage = image;
                    }
                }
                return largestImage;
            }
            catch( IOException ex )
            {   // this exception won't happen as the byte[] is simply wrapped, see Guava for a solution
                return null;
            }
            catch( BadIcoResException ex )
            {   // Web sites generally, but not always serve up an actual Microsoft .ICO, however, sometimes
                // it is a ".jpeg" or ".png" under the ".ico" extension, ex. "http://www.gnu.org/favicon.ico"
                return Toolkit.getDefaultToolkit().createImage( bytes );
            }
        }
    }
}
