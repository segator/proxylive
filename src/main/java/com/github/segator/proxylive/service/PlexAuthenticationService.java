/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.segator.proxylive.service;

import com.github.segator.proxylive.config.PlexAuthentication;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author isaac
 */
public class PlexAuthenticationService implements AuthenticationService {

    @Autowired
    private ProxyLiveConfiguration configuration;

    @Override
    public boolean loginUser(String user, String password) throws MalformedURLException, IOException, ParseException {
        PlexAuthentication plexAuthConfig = configuration.getAuthentication().getPlex();
        //Check user pass is valid
        JSONObject userData = getUserData(user, password);
        String userID = (String) userData.get("id").toString();
        //Check the user is registered to our plex server and have channels enabled
        URL url = new URL(String.format("https://%s:%s@plex.tv/api/users", URLEncoder.encode(plexAuthConfig.getAdminUser(),"UTF-8"), URLEncoder.encode(plexAuthConfig.getAdminPass(),"UTF-8")));
        HttpURLConnection connection = createConnection(url);
        connection.connect();
        if (connection.getResponseCode() != 200) {
            throw new IOException("unexpected error when getting users list:" + connection.getResponseCode());
        }
        Document dom = newDocumentFromInputStream(connection.getInputStream());
        NodeList users = dom.getElementsByTagName("User");
        for (int i = 0; i < users.getLength(); i++) {
            Element userEl = (Element) users.item(i);
            if (userEl.getAttribute("id").equals(userID) && userEl.getAttribute("allowChannels").equals("1")) {
                NodeList servers = userEl.getElementsByTagName("Server");
                if (servers.getLength() > 0) {
                    for (int j = 0; j < servers.getLength(); j++) {
                        Element server = (Element) servers.item(j);
                        if (server.getAttribute("name").equals(plexAuthConfig.getServerName())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public List<String> getUserGroups(String user) {
        ArrayList<String> userGroups = new ArrayList();
        userGroups.add("all");
        return userGroups;
    }

    private JSONObject getUserData(String user, String pass) throws MalformedURLException, IOException, ParseException {
        URL url = new URL(String.format("https://%s:%s@plex.tv/users/sign_in.json", URLEncoder.encode(user,"UTF-8"),  URLEncoder.encode(pass,"UTF-8")));
        HttpURLConnection connection = createConnection(url);
        connection.setRequestProperty("X-Plex-Client-Identifier", "proxylive");
        connection.setRequestMethod("POST");
        connection.connect();
        if (connection.getResponseCode() != 201) {
            throw new IOException(String.format("Cannot login as plex %s:%s", user, connection.getResponseCode()));
        }
        JSONParser jsonParser = new JSONParser();
        JSONObject response = (JSONObject) jsonParser.parse(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        return (JSONObject) response.get("user");
    }

    @PostConstruct
    private void initialize() throws MalformedURLException, ProtocolException, IOException, ParseException {
        PlexAuthentication plexAuthConfig = configuration.getAuthentication().getPlex();
        JSONObject userData = getUserData(plexAuthConfig.getAdminUser(), plexAuthConfig.getAdminPass());

    }

    private HttpURLConnection createConnection(URL url) throws ProtocolException, IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setReadTimeout(10000);
        if (url.getUserInfo() != null) {
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(URLDecoder.decode(url.getUserInfo(),"UTF-8").getBytes()));
            connection.setRequestProperty("Authorization", basicAuth);
        }
        connection.setRequestMethod("GET");
        connection.setReadTimeout(10000);
        return connection;
    }

    public Document newDocumentFromInputStream(InputStream in) {
        DocumentBuilderFactory factory = null;
        DocumentBuilder builder = null;
        Document ret = null;

        try {
            factory = DocumentBuilderFactory.newInstance();
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        try {
            ret = builder.parse(new InputSource(in));
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

}
