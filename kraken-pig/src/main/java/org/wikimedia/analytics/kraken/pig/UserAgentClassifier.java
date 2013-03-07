/**
 * Copyright (C) 2012-2013  Wikimedia Foundation

 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.wikimedia.analytics.kraken.pig;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.pig.EvalFunc;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.wikimedia.analytics.dclassjni.DclassWrapper;
import org.wikimedia.analytics.kraken.schemas.AppleUserAgent;
import org.wikimedia.analytics.kraken.schemas.JsonToClassConverter;
import org.wikimedia.analytics.kraken.schemas.Schema;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class uses the dClass mobile device user agent decision tree to determine vendor/version of a mobile device.
 * dClass is built on the OpenDDR project.
 *
 * NOTES:
 * 1) iPod devices are treated as if they are iPhones
 * 2) Not all Apple build id's are recognized
 * 3) Browser version is poorly supported by openDDR, especially for Apple devices
 * 4) The Apple post processor function does try to fix of the openDDR issues but it is definitely not perfect.
 */
public class UserAgentClassifier extends EvalFunc<Tuple> {
    /** Factory to generate Pig tuples */
    private TupleFactory tupleFactory = TupleFactory.getInstance();

    private DclassWrapper dw = null;
    private String useragent = null;
    private Map result = new HashMap<String, String>();
    //private List args = new ArrayList<String>();
    //private final List knownArgs = new ArrayList<String>();

    //Additional Apple device recognizers
    private Pattern appleBuildIdentifiers = Pattern.compile("(\\d{1,2}[A-L]\\d{1,3}a?)");
    private HashMap<String, Schema> appleProducts = new HashMap<String, Schema>();

    // Wikimedia Mobile Apps regular expressions
    private Pattern android = Pattern.compile("WikipediaMobile\\/\\d\\.\\d(\\.\\d)?");
    private Pattern firefox = Pattern.compile(Pattern.quote("Mozilla/5.0 (Mobile; rv:18.0) Gecko/18.0 Firefox/18.0")); //VERIFIED
    private Pattern rim = Pattern.compile(Pattern.quote("Mozilla/5.0 (PlayBook; U; RIM Tablet OS 2.1.0; en-US) AppleWebKit/536.2+ (KHTML, like Gecko) Version/7.2.1.0 Safari/536.2+"));
    private Pattern windows = Pattern.compile(Pattern.quote("Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2; Trident/6.0; MSAppHost/1.0)")); //VERIFIED

    private Map<String, Pattern> mobileAppPatterns;

    /**
     * * UserAgentClassifier constructor that loads:
     * 1) dClass library
     * 2) initializes Wikimedia Mobile app regular expressions
     * 3) load JSON with Apple iOS specific information
     *
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    public UserAgentClassifier() throws JsonMappingException, JsonParseException {
        if (this.dw == null) {
            this.dw = new DclassWrapper();
        }
        //knownArgs.add(0,);
        this.dw.initUA();
        mobileAppPatterns = new HashMap<String, Pattern>();
        mobileAppPatterns.put("Wikimedia App Firefox", firefox);
        mobileAppPatterns.put("Wikimedia App Android", android);
        mobileAppPatterns.put("Wikimedia App RIM", rim);
        mobileAppPatterns.put("Wikimedia App Windows", windows);

        JsonToClassConverter converter = new JsonToClassConverter();
        this.appleProducts = converter.construct("org.wikimedia.analytics.kraken.schemas.AppleUserAgent", "ios.json", "getProduct");
    }

    /**
     *
     * @param args
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    public UserAgentClassifier(final String[] args) throws JsonMappingException, JsonParseException {
        this();
        mobileAppPatterns = new HashMap<String, Pattern>();
    }

    /**
     *
     * @param useragent
     * @return useragent without spaces encoded as %20
     */
    private String unspace(String useragent) {
        return useragent.replace("%20", " ");
    }

    /**
     * If the useragent string is not identified as a mobile device using dClass
     * then we need to determine whether it's an Wikimedia mobile app. This
     * function iterates over a list of regular expressions to look for a match.
     *
     * @param output
     * @return
     * @throws ExecException
     */
    private Tuple detectMobileApp(Tuple output) throws ExecException {
        Pattern pattern;
        boolean foundMatch = false;
        for (Map.Entry<String, Pattern> entry : mobileAppPatterns.entrySet()) {
            pattern = entry.getValue();
            Matcher matcher = pattern.matcher(this.useragent);
            if (matcher.find()) {
                output.set(5, entry.getKey());
                foundMatch = true;
                break;
            }
        }
        if (!foundMatch) {
            output.set(5, null);
        }
        return output;
    }


    /**
     * {@inheritDoc}
     *
     * Method exec takes a {@link Tuple} which should contain a single field, namely the user agent string.
     *
     * returns a tuple with the following fields:
     * 1) Vendor (String)
     * 2) Device OS (String)
     * 3) Device OS version (not for iOS) (String)
     * 4) isWirelessDevice (boolean)
     * 5) isTablet (boolean)
     * 6) Wikimedia Mobile app or null
     * 7) Apple iOS specific information or null
     */
    @Override
    public final Tuple exec(final Tuple input) throws IOException {
        if (input == null || input.size() != 1 || input.get(0) == null) {
            return null;
        }

        this.useragent = unspace(input.get(0).toString());
        result = this.dw.classifyUA(this.useragent);

        Tuple output = tupleFactory.newTuple(7);
        output.set(0, result.get("vendor"));
        output.set(1, result.get("device_os"));
        output.set(2, result.get("device_os_version"));
        output.set(3, convertToBoolean(result, "is_wireless_device"));
        output.set(4, convertToBoolean(result, "is_tablet"));

        output = detectMobileApp(output);  // field 5 contains mobile app info
        output = postProcessApple(output); // field 6 contains additional iOS version info.
        return output;
    }

    /**
     * Converts the 'true' or 'false' string to a boolean
     * @param result
     * @param param
     * @return boolean
     */
    private boolean convertToBoolean(final Map<String, String> result, final String param) {
        return Boolean.parseBoolean(result.get(param));
    }


    /**
     * dClass has identified the mobile device as one from Apple but unfortunately
     * it does not provide reliable iOS version information. This function
     * adds iOS information but care should be used when this data is interpreted:
     * The iOS version is determined using the build number and hence the iOS field
     * should be read as "this mobile device has at least iOS version xyz running".
     *
     * @param output
     * @return
     * @throws ExecException
     */

    private Tuple postProcessApple(Tuple output) throws ExecException {
        Matcher match = appleBuildIdentifiers.matcher(this.useragent);
        if (match.find()) {
            String build = match.group(0).toString();
            String device;
            boolean isTablet = (Boolean) output.get(4);
            if (isTablet) {
                device = "iPad";
            } else {
                device = (String) output.get(1);
            }
            String key = device.split(" ")[0] + "-" + build;
            AppleUserAgent appleUserAgent = (AppleUserAgent) this.appleProducts.get(key);
            if (appleUserAgent != null) {
                output.set(6, appleUserAgent.toString());
            } else {
                output.set(6, "unknown.apple.build.id");
            }
        } else {
            output.set(6, null);
        }
        return output;
    }

    /**
     *
     * @param output
     * @return
     * @throws ExecException
     */
    private Tuple postProcessSamsung(Tuple output) throws ExecException {
        /**
         * This function takes a Samsung model (GT-S5750E, GT S5620) and drops all
         * suffix characters and digits to allow for rollup of the keys.
         */

        String model = (String) result.get("model");
        Matcher m = this.android.matcher(model);
        if (m.matches() && m.groupCount() == 4) {
            String name = m.group(1);
            String value = m.group(3);
            String valueCleaned = value.replaceAll("\\d", "");
            String modelCleaned = name + "-" + valueCleaned;
            output.set(2, modelCleaned);
        } else {
            output.set(2, m.group(0));
        }
        return output;
    }

    /**
     * Call the custom finalizer to free the memory from the C library
     */
    protected final void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        this.dw.destroyUA();
    }
}
