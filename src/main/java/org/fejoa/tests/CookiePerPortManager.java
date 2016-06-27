//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// Customized version of the default CookieManager that manages cookies per port.
// This is uses for testing of multiple independent clients on a local host.
package org.fejoa.tests;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import sun.util.logging.PlatformLogger;

class InMemoryCookieStore implements CookieStore {
    private List<HttpCookie> cookieJar = null;
    private Map<String, List<HttpCookie>> domainIndex = null;
    private Map<URI, List<HttpCookie>> uriIndex = null;
    private ReentrantLock lock = null;

    public InMemoryCookieStore() {
        this.cookieJar = new ArrayList();
        this.domainIndex = new HashMap();
        this.uriIndex = new HashMap();
        this.lock = new ReentrantLock(false);
    }

    public void add(URI uri, HttpCookie cookie) {
        if(cookie == null) {
            throw new NullPointerException("cookie is null");
        } else {
            this.lock.lock();

            try {
                this.cookieJar.remove(cookie);
                if(cookie.getMaxAge() != 0L) {
                    this.cookieJar.add(cookie);
                    if(cookie.getDomain() != null) {
                        this.addIndex(this.domainIndex, cookie.getDomain(), cookie);
                    }

                    if(uri != null) {
                        this.addIndex(this.uriIndex, this.getEffectiveURI(uri), cookie);
                    }
                }
            } finally {
                this.lock.unlock();
            }

        }
    }

    public List<HttpCookie> get(URI uri) {
        if(uri == null) {
            throw new NullPointerException("uri is null");
        } else {
            ArrayList cookies = new ArrayList();
            boolean secureLink = "https".equalsIgnoreCase(uri.getScheme());
            this.lock.lock();

            try {
                this.getInternal1(cookies, this.domainIndex, uri.getHost(), secureLink);
                this.getInternal2(cookies, this.uriIndex, this.getEffectiveURI(uri), secureLink);
            } finally {
                this.lock.unlock();
            }

            return cookies;
        }
    }

    public List<HttpCookie> getCookies() {
        this.lock.lock();

        List rt;
        try {
            Iterator it = this.cookieJar.iterator();

            while(it.hasNext()) {
                if(((HttpCookie)it.next()).hasExpired()) {
                    it.remove();
                }
            }
        } finally {
            rt = Collections.unmodifiableList(this.cookieJar);
            this.lock.unlock();
        }

        return rt;
    }

    public List<URI> getURIs() {
        ArrayList uris = new ArrayList();
        this.lock.lock();

        try {
            Iterator it = this.uriIndex.keySet().iterator();

            while(it.hasNext()) {
                URI uri = (URI)it.next();
                List cookies = (List)this.uriIndex.get(uri);
                if(cookies == null || cookies.size() == 0) {
                    it.remove();
                }
            }
        } finally {
            uris.addAll(this.uriIndex.keySet());
            this.lock.unlock();
        }

        return uris;
    }

    public boolean remove(URI uri, HttpCookie ck) {
        if(ck == null) {
            throw new NullPointerException("cookie is null");
        } else {
            boolean modified = false;
            this.lock.lock();

            try {
                modified = this.cookieJar.remove(ck);
            } finally {
                this.lock.unlock();
            }

            return modified;
        }
    }

    public boolean removeAll() {
        this.lock.lock();

        try {
            this.cookieJar.clear();
            this.domainIndex.clear();
            this.uriIndex.clear();
        } finally {
            this.lock.unlock();
        }

        return true;
    }

    private boolean netscapeDomainMatches(String domain, String host) {
        if(domain != null && host != null) {
            boolean isLocalDomain = ".local".equalsIgnoreCase(domain);
            int embeddedDotInDomain = domain.indexOf(46);
            if(embeddedDotInDomain == 0) {
                embeddedDotInDomain = domain.indexOf(46, 1);
            }

            if(!isLocalDomain && (embeddedDotInDomain == -1 || embeddedDotInDomain == domain.length() - 1)) {
                return false;
            } else {
                int firstDotInHost = host.indexOf(46);
                if(firstDotInHost == -1 && isLocalDomain) {
                    return true;
                } else {
                    int domainLength = domain.length();
                    int lengthDiff = host.length() - domainLength;
                    if(lengthDiff == 0) {
                        return host.equalsIgnoreCase(domain);
                    } else if(lengthDiff > 0) {
                        host.substring(0, lengthDiff);
                        String D = host.substring(lengthDiff);
                        return D.equalsIgnoreCase(domain);
                    } else {
                        return lengthDiff != -1?false:domain.charAt(0) == 46 && host.equalsIgnoreCase(domain.substring(1));
                    }
                }
            }
        } else {
            return false;
        }
    }

    private void getInternal1(List<HttpCookie> cookies, Map<String, List<HttpCookie>> cookieIndex, String host, boolean secureLink) {
        ArrayList toRemove = new ArrayList();
        Iterator i$ = cookieIndex.entrySet().iterator();

        label66:
        while(i$.hasNext()) {
            Map.Entry entry = (Map.Entry)i$.next();
            String domain = (String)entry.getKey();
            List lst = (List)entry.getValue();
            Iterator i$1 = lst.iterator();

            while(true) {
                HttpCookie c;
                label55:
                do {
                    while(true) {
                        while(true) {
                            do {
                                if(!i$1.hasNext()) {
                                    i$1 = toRemove.iterator();

                                    while(i$1.hasNext()) {
                                        c = (HttpCookie)i$1.next();
                                        lst.remove(c);
                                        this.cookieJar.remove(c);
                                    }

                                    toRemove.clear();
                                    continue label66;
                                }

                                c = (HttpCookie)i$1.next();
                            } while((c.getVersion() != 0 || !this.netscapeDomainMatches(domain, host)) && (c.getVersion() != 1 || !HttpCookie.domainMatches(domain, host)));

                            if(this.cookieJar.indexOf(c) != -1) {
                                if(!c.hasExpired()) {
                                    continue label55;
                                }

                                toRemove.add(c);
                            } else {
                                toRemove.add(c);
                            }
                        }
                    }
                } while(!secureLink && c.getSecure());

                if(!cookies.contains(c)) {
                    cookies.add(c);
                }
            }
        }

    }

    private <T> void getInternal2(List<HttpCookie> cookies, Map<T, List<HttpCookie>> cookieIndex, Comparable<T> comparator, boolean secureLink) {
        Iterator i$ = cookieIndex.keySet().iterator();

        label53:
        while(true) {
            List indexedCookies;
            do {
                Object index;
                do {
                    if(!i$.hasNext()) {
                        return;
                    }

                    index = i$.next();
                } while(comparator.compareTo((T) index) != 0);

                indexedCookies = (List)cookieIndex.get(index);
            } while(indexedCookies == null);

            Iterator it = indexedCookies.iterator();

            while(true) {
                HttpCookie ck;
                label49:
                do {
                    while(true) {
                        while(true) {
                            if(!it.hasNext()) {
                                continue label53;
                            }

                            ck = (HttpCookie)it.next();
                            if(this.cookieJar.indexOf(ck) != -1) {
                                if(!ck.hasExpired()) {
                                    continue label49;
                                }

                                it.remove();
                                this.cookieJar.remove(ck);
                            } else {
                                it.remove();
                            }
                        }
                    }
                } while(!secureLink && ck.getSecure());

                if(!cookies.contains(ck)) {
                    cookies.add(ck);
                }
            }
        }
    }

    private <T> void addIndex(Map<T, List<HttpCookie>> indexStore, T index, HttpCookie cookie) {
        if(index != null) {
            List cookies = (List)indexStore.get(index);
            if(cookies != null) {
                cookies.remove(cookie);
                cookies.add(cookie);
            } else {
                ArrayList cookies1 = new ArrayList();
                cookies1.add(cookie);
                indexStore.put(index, cookies1);
            }
        }

    }

    private URI getEffectiveURI(URI uri) {
        return uri;
        /*URI effectiveURI = null;

        try {
            effectiveURI = new URI("http", uri.getHost(), (String)null, (String)null, (String)null);
        } catch (URISyntaxException var4) {
            effectiveURI = uri;
        }

        return effectiveURI;*/
    }
}

public class CookiePerPortManager extends CookieHandler {
    private CookiePolicy policyCallback;
    private CookieStore cookieJar;

    public CookiePerPortManager() {
        this((CookieStore)null, (CookiePolicy)null);
    }

    public CookiePerPortManager(CookieStore store, CookiePolicy cookiePolicy) {
        this.cookieJar = null;
        this.policyCallback = cookiePolicy == null?CookiePolicy.ACCEPT_ORIGINAL_SERVER:cookiePolicy;
        if(store == null) {
            this.cookieJar = new InMemoryCookieStore();
        } else {
            this.cookieJar = store;
        }

    }

    public void setCookiePolicy(CookiePolicy cookiePolicy) {
        if(cookiePolicy != null) {
            this.policyCallback = cookiePolicy;
        }

    }

    public CookieStore getCookieStore() {
        return this.cookieJar;
    }

    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
        if(uri != null && requestHeaders != null) {
            HashMap cookieMap = new HashMap();
            if(this.cookieJar == null) {
                return Collections.unmodifiableMap(cookieMap);
            } else {
                boolean secureLink = "https".equalsIgnoreCase(uri.getScheme());
                ArrayList cookies = new ArrayList();
                String path = uri.getPath();
                if(path == null || path.isEmpty()) {
                    path = "/";
                }

                Iterator cookieHeader = this.cookieJar.get(uri).iterator();

                while(true) {
                    while(true) {
                        HttpCookie cookie;
                        String ports;
                        do {
                            do {
                                do {
                                    if(!cookieHeader.hasNext()) {
                                        List cookieHeader1 = this.sortByPath(cookies);
                                        cookieMap.put("Cookie", cookieHeader1);
                                        return Collections.unmodifiableMap(cookieMap);
                                    }

                                    cookie = (HttpCookie)cookieHeader.next();
                                } while(!this.pathMatches(path, cookie.getPath()));
                            } while(!secureLink && cookie.getSecure());

                            if(!cookie.isHttpOnly()) {
                                break;
                            }

                            ports = uri.getScheme();
                        } while(!"http".equalsIgnoreCase(ports) && !"https".equalsIgnoreCase(ports));

                        ports = cookie.getPortlist();
                        if(ports != null && !ports.isEmpty()) {
                            int port = uri.getPort();
                            if(port == -1) {
                                port = "https".equals(uri.getScheme())?443:80;
                            }

                            if(isInPortList(ports, port)) {
                                cookies.add(cookie);
                            }
                        } else {
                            cookies.add(cookie);
                        }
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Argument is null");
        }
    }

    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
        if(uri != null && responseHeaders != null) {
            if(this.cookieJar != null) {
                PlatformLogger logger = PlatformLogger.getLogger("java.net.CookieManager");
                Iterator i$ = responseHeaders.keySet().iterator();

                while(true) {
                    String headerKey;
                    do {
                        do {
                            if(!i$.hasNext()) {
                                return;
                            }

                            headerKey = (String)i$.next();
                        } while(headerKey == null);
                    } while(!headerKey.equalsIgnoreCase("Set-Cookie2") && !headerKey.equalsIgnoreCase("Set-Cookie"));

                    Iterator i$1 = ((List)responseHeaders.get(headerKey)).iterator();

                    while(i$1.hasNext()) {
                        String headerValue = (String)i$1.next();

                        try {
                            List cookies;
                            try {
                                cookies = HttpCookie.parse(headerValue);
                            } catch (IllegalArgumentException var13) {
                                cookies = Collections.EMPTY_LIST;
                                if(logger.isLoggable(PlatformLogger.Level.SEVERE)) {
                                    logger.severe("Invalid cookie for " + uri + ": " + headerValue);
                                }
                            }

                            Iterator i$2 = cookies.iterator();

                            while(i$2.hasNext()) {
                                HttpCookie cookie = (HttpCookie)i$2.next();
                                String ports;
                                int port;
                                if(cookie.getPath() == null) {
                                    ports = uri.getPath();
                                    if(!ports.endsWith("/")) {
                                        port = ports.lastIndexOf("/");
                                        if(port > 0) {
                                            ports = ports.substring(0, port + 1);
                                        } else {
                                            ports = "/";
                                        }
                                    }

                                    cookie.setPath(ports);
                                }

                                if(cookie.getDomain() == null) {
                                    cookie.setDomain(uri.getHost());
                                }

                                ports = cookie.getPortlist();
                                if(ports != null) {
                                    port = uri.getPort();
                                    if(port == -1) {
                                        port = "https".equals(uri.getScheme())?443:80;
                                    }

                                    if(ports.isEmpty()) {
                                        cookie.setPortlist("" + port);
                                        if(this.shouldAcceptInternal(uri, cookie)) {
                                            this.cookieJar.add(uri, cookie);
                                        }
                                    } else if(isInPortList(ports, port) && this.shouldAcceptInternal(uri, cookie)) {
                                        this.cookieJar.add(uri, cookie);
                                    }
                                } else if(this.shouldAcceptInternal(uri, cookie)) {
                                    this.cookieJar.add(uri, cookie);
                                }
                            }
                        } catch (IllegalArgumentException var14) {
                            ;
                        }
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Argument is null");
        }
    }

    private boolean shouldAcceptInternal(URI uri, HttpCookie cookie) {
        try {
            return this.policyCallback.shouldAccept(uri, cookie);
        } catch (Exception var4) {
            return false;
        }
    }

    private static boolean isInPortList(String lst, int port) {
        int i = lst.indexOf(",");

        int val1;
        for(boolean val = true; i > 0; i = lst.indexOf(",")) {
            try {
                val1 = Integer.parseInt(lst.substring(0, i));
                if(val1 == port) {
                    return true;
                }
            } catch (NumberFormatException var6) {
                ;
            }

            lst = lst.substring(i + 1);
        }

        if(!lst.isEmpty()) {
            try {
                val1 = Integer.parseInt(lst);
                if(val1 == port) {
                    return true;
                }
            } catch (NumberFormatException var5) {
                ;
            }
        }

        return false;
    }

    private boolean pathMatches(String path, String pathToMatchWith) {
        return path == pathToMatchWith?true:(path != null && pathToMatchWith != null?path.startsWith(pathToMatchWith):false);
    }

    private List<String> sortByPath(List<HttpCookie> cookies) {
        Collections.sort(cookies, new CookiePerPortManager.CookiePathComparator());
        ArrayList cookieHeader = new ArrayList();

        HttpCookie cookie;
        for(Iterator i$ = cookies.iterator(); i$.hasNext(); cookieHeader.add(cookie.toString())) {
            cookie = (HttpCookie)i$.next();
            if(cookies.indexOf(cookie) == 0 && cookie.getVersion() > 0) {
                cookieHeader.add("$Version=\"1\"");
            }
        }

        return cookieHeader;
    }

    static class CookiePathComparator implements Comparator<HttpCookie> {
        CookiePathComparator() {
        }

        public int compare(HttpCookie c1, HttpCookie c2) {
            return c1 == c2?0:(c1 == null?-1:(c2 == null?1:(!c1.getName().equals(c2.getName())?0:(c1.getPath().startsWith(c2.getPath())?-1:(c2.getPath().startsWith(c1.getPath())?1:0)))));
        }
    }
}
