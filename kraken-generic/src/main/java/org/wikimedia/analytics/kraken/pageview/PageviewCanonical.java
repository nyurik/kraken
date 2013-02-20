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
package org.wikimedia.analytics.kraken.pageview;


import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class contains detailed business logic to simplify a the url of a valid pageview into the canonical title
 * of the page.
 */
public class PageviewCanonical {
    private StringBuilder sb;
    private Pattern action = Pattern.compile("action=[a-z]*");
    private Matcher matcher;


    /**
     *
     * @param titleInput an UTF-8 encoded String containing the article title
     * @return the decoded article title
     * @throws UnsupportedEncodingException
     */
    private String decodeURL(final String titleInput)  {
        String title = null;
        try {
            title = URLDecoder.decode(titleInput, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // do nothing, this will never happen
        } catch (IllegalArgumentException e) {
            return titleInput;
        }
        return title;
    }
    /**
     * This function is specifically written for PageviewType.IMAGE pageviews.
     * @param url
     * @return
     */
    private String parsePath(final URL url) {
        // http://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Nakhalfarms.jpg/220px-Nakhalfarms.jpg
        String pathWithoutPrefix = url.getPath().replaceAll("/wikipedia/[a-z]*/thumb/[a-z0-9]{1}/[a-z0-9]{2}/", "");
        int positionRightSlash = pathWithoutPrefix.lastIndexOf("/");
        String pathWithoutThumb;
        if (positionRightSlash > 0) {
            pathWithoutThumb = pathWithoutPrefix.substring(0, positionRightSlash);
        } else {
            pathWithoutThumb = pathWithoutPrefix;
        }

        String path;
        if (!pathWithoutThumb.toLowerCase().endsWith(".jpg")
                || !pathWithoutThumb.toLowerCase().endsWith(".png")
                || !pathWithoutThumb.toLowerCase().endsWith(".svg")) {
            positionRightSlash = pathWithoutThumb.lastIndexOf("/");
            if (positionRightSlash > 0) {
                path = pathWithoutThumb.substring(0, positionRightSlash);
            } else {
                path =  pathWithoutThumb;
            }
        }  else {
            path =  pathWithoutThumb;
        }
        return path;
    }

    /**
     * Determine the language, project of a pageview.
     * @param url
     * @param pageviewType
     * @return
     */
    private String getProject(final URL url, final PageviewType pageviewType){
        sb = new StringBuilder();
        String[] hostname = url.getHost().split("\\.");
        if (pageviewType == PageviewType.MOBILE || pageviewType == PageviewType.MOBILE_API) {
            sb.append(hostname[0]);
            sb.append(".");
            sb.append(hostname[1]);
            sb.append(".");
            sb.append(hostname[2]);
        }  else if (pageviewType == PageviewType.IMAGE) {
            sb.append(hostname[0]);
        } else {
            sb.append(hostname[0]);
            sb.append(".");
            sb.append(hostname[1]);
        }
        return sb.toString();

    }

    /**
     *
     * @param url
     * @parm pageviewType
     * @return
     */
    public String canonicalizeDesktopPageview(final URL url, final PageviewType pageviewType) {
        String project = getProject(url, pageviewType);
        String titleInput = url.getPath().replace("/wiki/", "");
        String title = decodeURL(titleInput);
        sb = new StringBuilder();
        sb.append(project);
        sb.append(" ");
        sb.append(title);
        return sb.toString();
    }

    /**
     *
     * @param url
     * @parm pageviewType
     * @return
     */
    public String canonicalizeMobilePageview(final URL url, final PageviewType pageviewType) {
        String project = getProject(url, pageviewType);
        String titleInput = url.getPath().replace("/wiki/", "");
        String title = decodeURL(titleInput);
        sb = new StringBuilder();
        sb.append(project);
        sb.append(" ");
        sb.append(title);
        return sb.toString();
    }

    /**
     *
     * @param url
     * @return
     */
    public String canonicalizeApiRequest(final URL url, final PageviewType pageviewType) {
        String project = getProject(url, pageviewType);
        String query;

        if (url.getQuery() != null) {
            try {
                 query = action.matcher(url.getQuery()).group(0);
            } catch (IllegalStateException e) {
                query = "unknown.api.action";
            }
        } else {
            query = "unknown.api.action";
        }
        sb = new StringBuilder();
        sb.append(project);
        sb.append(" ");
        sb.append(query);
        return sb.toString();
    }

    /**
     *
     * @param url
     * @parm pageviewType
     * @return
     */
    public String canonicalizeBlogPageview(final URL url, final PageviewType pageviewType) {
        //TODO not yet implemented
        return url.toString();
    }

    /**
     *
     * @param url
     * @parm pageviewType
     * @return
     */
    public String canonicalizeSearchQuery(final URL url, final PageviewType pageviewType) {
        return url.toString();
    }

    /**
     * This function canonicalizes an imageview as follows:
     * Given thumbail view https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Acueducto_de_Segovia_01.jpg/600px-Acueducto_de_Segovia_01.jpg
     * that becomes upload Acueducto_de_Segovia_01.jpg
     * @param url
     * @parm pageviewType
     * @return
     */
    public String canonicalizeImagePageview(final URL url, final PageviewType pageviewType) {

        String path = parsePath(url);
        String project = getProject(url, pageviewType);

        sb = new StringBuilder();
        sb.append(project);
        sb.append(" ");
        sb.append(path);
        return sb.toString();
    }
}
