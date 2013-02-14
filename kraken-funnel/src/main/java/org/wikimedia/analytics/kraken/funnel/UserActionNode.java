/**
 *Copyright (C) 2012-2013  Wikimedia Foundation
 *
 *This program is free software; you can redistribute it and/or
 *modify it under the terms of the GNU General Public License
 *as published by the Free Software Foundation; either version 2
 *of the License, or (at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program; if not, write to the Free Software
 *Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 */

package org.wikimedia.analytics.kraken.funnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Inspiration taken from "The Uniﬁed Logging Infrastructure for Data Analytics at Twitter".
 * (http://vldb.org/pvldb/vol5/p1771_georgelee_vldb2012.pdf)
 * Component    Description                 Example
 * client       client application          web, iphone, android
 * languagecode project language            en, fr, ja
 * project      project name                wikipedia, wikisource
 * namespace                                0, 2
 * page                                     page or functional grouping
 * event        action taken by user        impression, click
 *
 * suggested encoding of an event:
 * client:languagecode:project:namespace:page:event
 *

 */
class UserActionNode extends Node{
    /** The params. */
    public Map<ComponentType, String> componentValues;
    private final Pattern wikis = Pattern.compile("project:(patternForProject)|||language:(patternForLanguage)"); //TODO: Find actual regex
    //private boolean reachedPreviousNode = true;
    //public List<Date> visited;
    //public String url;

    /**
     * Instantiates a new node.
     *
     * @param json {@link com.google.gson.JsonObject} NOTE: keys must be upper-case
     */
    public UserActionNode(final JsonObject json)  {
        /**
         * // TODO: this gets repeated for every instantiation so it should be
         * factored out, this will be taken care of once we store the EventLogging
         * data using AVRO.
         *
         * This function takes as input different fields from the EventLogging
         * extension  and turns it into a single string that is in the form:
         * client:languagecode:project:namespace:page:event {@link Node}
         *
         * @param project the project variable as generated by the EventLogging extension.
         * @param namespace the namespace variable as generated by the EventLogging extension.
         * @param page the page variable as generated by the EventLogging extension.
         * @param event the event variable as generated by the EventLogging extension.
         */
        componentValues = new HashMap<ComponentType, String>();
        for (ComponentType type : ComponentType.values()) {
            JsonElement value = json.get(type.toString().toLowerCase());
            if (value != null && value.isJsonPrimitive()){
                String valueString = value.getAsString();
                switch (type) {
                    case PROJECT:
                        componentValues.putAll(splitProject(valueString));
                        break;
                    default:
                        componentValues.put(type, valueString);
                        break;
                }
            }
        }
    }


    /**
     * Split project variable from EventLogging data in language and project
     * component.
     *
     * @param project the new project
     */
    private HashMap<ComponentType, String> splitProject(final String project) {
        MatchResult match = this.wikis.matcher(project);
        HashMap<ComponentType, String> ret = new HashMap<ComponentType, String>();
        ret.put(ComponentType.PROJECT, match.group(0));
        ret.put(ComponentType.LANGUAGE, match.group(1));
        return ret;
    }

    /**
     * Compare two nodes and determine whether they can be considered the same.
     * @param obj
     * @return
     */

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof FunnelNode) { return ((FunnelNode) obj).matches(this); }
        if (!(obj instanceof UserActionNode)) { return false; }

        return new EqualsBuilder().
                append(this.toString(), obj.toString()).
                isEquals();
    }

    public int hashCode() {
        return new HashCodeBuilder().append(this.toString()).toHashCode();
    }

    public String toString() {
        List<String> sb = new LinkedList<String>();
        for (ComponentType key : ComponentType.values()) {
            String value = componentValues.get(key);
            if (value != null) {
                sb.add(value.isEmpty() ? "." : value);
            }
        }
        if (!sb.isEmpty()) {
            return StringUtils.join(sb, ':');
        } else {
            return "ZERO";
        }

    }
}
