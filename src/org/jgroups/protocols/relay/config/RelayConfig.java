package org.jgroups.protocols.relay.config;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Parses and maintains the RELAY2 configuration (in memory)
 * @author Bela Ban
 * @since 3.2
 */
public class RelayConfig {
    protected static final String RELAY_CONFIG  = "RelayConfiguration";
    protected static final String SITES         = "sites";
    protected static final String SITE          = "site";
    protected static final String BRIDGES       = "bridges";
    protected static final String BRIDGE        = "bridge";
    protected static final String FORWARDS      = "forwards";
    protected static final String FORWARD       = "forward";
    


    /*public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("sites:\n");
        for(Map.Entry<String,SiteConfig> entry: sites.entrySet())
            sb.append(entry.getKey() + " --> " + entry.getValue() + "\n");

        return sb.toString();
    }*/

    /** Returns a map between of site names and their configuration, e.g. "nyc" --> SiteConfig */
    public static Map<String,SiteConfig> parse(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false); // for now
        DocumentBuilder builder=factory.newDocumentBuilder();
        Document document=builder.parse(input);
        Element root=document.getDocumentElement();
        match(RELAY_CONFIG, root.getNodeName(), true);
        Map<String,SiteConfig> retval=new HashMap<String,SiteConfig>();
        NodeList children=root.getChildNodes();
        if(children == null || children.getLength() == 0)
            return retval;
        for(int i=0; i < children.getLength(); i++) {
            Node node=children.item(i);
            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String element_name=node.getNodeName();
            if(SITES.equals(element_name))
                parseSites(retval, node);
            else
                throw new Exception("expected <" + SITES + ">, but got " + "<" + element_name + ">");
        }
        return retval;
    }


    protected static void parseSites(Map<String,SiteConfig> map, Node root) throws Exception {
        NodeList children=root.getChildNodes();
        if(children == null || children.getLength() == 0)
            return;
        for(int i=0; i < children.getLength(); i++) {
            Node node=children.item(i);
            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            match(SITE, node.getNodeName(), true);
            NamedNodeMap attrs=node.getAttributes();
            if(attrs == null || attrs.getLength() == 0)
                continue;
            Attr name_attr=(Attr)attrs.getNamedItem("name");
            Attr id_attr=(Attr)attrs.getNamedItem("id");

            String name=name_attr.getValue();
            short id=Short.parseShort(id_attr.getValue());
            if(id <= 0)
                throw new Exception("Site ID must be > 0");

            if(map.containsKey(name))
                throw new Exception("Site \"" + name + "\" already defined");
            SiteConfig site_config=new SiteConfig(name, id);
            map.put(name, site_config);

            parseBridgesAndForwards(site_config, node);
        }

        // Verify that all sites have unique IDs
        Set<Short> ids=new HashSet<Short>();
        for(SiteConfig site_config: map.values()) {
            if(ids.contains(site_config.getId()))
                throw new Exception("Site ID \"" + site_config.getId() + "\" is defined multiple times (must be unique)");
            ids.add(site_config.getId());
        }
    }

    protected static void parseBridgesAndForwards(SiteConfig site_config, Node root) throws Exception {
        NodeList children=root.getChildNodes();
        if(children == null || children.getLength() == 0)
            return;
        for(int i=0; i < children.getLength(); i++) {
            Node node=children.item(i);
            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String node_name=node.getNodeName();

            if(BRIDGES.equals(node_name))
                parseBridges(site_config, node);
            else if(FORWARDS.equals(node_name))
                parseForwards(site_config, node);
            else
                throw new Exception("expected \"" + BRIDGES + "\" or \"" + FORWARDS + "\" keywords");
        }
    }

    protected static void parseBridges(SiteConfig site_config, Node root) throws Exception {
        NodeList children=root.getChildNodes();
        if(children == null || children.getLength() == 0)
            return;
        for(int i=0; i < children.getLength(); i++) {
            Node node=children.item(i);
            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String node_name=node.getNodeName();
            match(BRIDGE, node_name, true);

            NamedNodeMap attrs=node.getAttributes();
            if(attrs == null || attrs.getLength() == 0)
                continue;
            Attr name_attr=(Attr)attrs.getNamedItem("name");
            Attr config_attr=(Attr)attrs.getNamedItem("config");
            String name=name_attr != null? name_attr.getValue() : null;
            String config=config_attr.getValue();
            BridgeConfig bridge_config=new BridgeConfig(name, config);
            site_config.addBridge(bridge_config);
        }
    }

    protected static void parseForwards(SiteConfig site_config, Node root) throws Exception {
        NodeList children=root.getChildNodes();
        if(children == null || children.getLength() == 0)
            return;
        for(int i=0; i < children.getLength(); i++) {
            Node node=children.item(i);
            if(node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String node_name=node.getNodeName();
            match(FORWARD, node_name, true);

            NamedNodeMap attrs=node.getAttributes();
            if(attrs == null || attrs.getLength() == 0)
                continue;
            Attr to_attr=(Attr)attrs.getNamedItem("to");
            Attr gw_attr=(Attr)attrs.getNamedItem("gateway");
            String to=to_attr.getValue();
            String gateway=gw_attr.getValue();
            ForwardConfig forward_config=new ForwardConfig(to, gateway);
            site_config.addForward(forward_config);
        }
    }


    protected static void match(String expected_name, String name, boolean is_element) throws Exception {
        if(!expected_name.equals(name))
            throw new Exception((is_element? "Element " : "Attribute ") + "\"" + name + "\" didn't match \"" + expected_name + "\"");
    }

    public static class SiteConfig {
        protected final String              name;
        protected final short               id;
        protected final List<BridgeConfig>  bridges=new ArrayList<BridgeConfig>();
        protected final List<ForwardConfig> forwards=new ArrayList<ForwardConfig>();

        public SiteConfig(String name, short id) {
            this.name=name;
            this.id=id;
        }

        public short getId()    {return id;}
        public String getName() {return name;}


        public List<BridgeConfig>  getBridges()   {return bridges;}
        public List<ForwardConfig> getForwards()  {return forwards;}

        public void addBridge(BridgeConfig bridge_config)    {bridges.add(bridge_config);}
        public void addForward(ForwardConfig forward_config) {forwards.add(forward_config);}

        public String toString() {
            StringBuilder sb=new StringBuilder("name=" + name + " (id=" + id + ")\n");
            if(!bridges.isEmpty())
                for(BridgeConfig bridge_config: bridges)
                    sb.append(bridge_config).append("\n");
            if(!forwards.isEmpty())
                for(ForwardConfig forward_config: forwards)
                    sb.append(forward_config).append("\n");
            return sb.toString();
        }
    }

    public static class BridgeConfig {
        protected final String name;
        protected final String config;

        public BridgeConfig(String name, String config) {
            this.name=name;
            this.config=config;
        }

        public String getConfig() {return config;}
        public String getName()   {return name;}

        public String toString() {
            return "config=" + config + (name != null? " (name=" + name + ")" : "");
        }
    }

    public static class ForwardConfig {
        protected final String to;
        protected final String gateway;

        public ForwardConfig(String to, String gateway) {
            this.to=to;
            this.gateway=gateway;
        }

        public String getGateway() {return gateway;}
        public String getTo()      {return to;}

        public String toString() {
            return "forward to=" + to + " gateway=" + gateway;
        }
    }

    public static void main(String[] args) throws Exception {
        InputStream input=new FileInputStream("/home/bela/relay1.xml");
        Map<String,SiteConfig> sites=RelayConfig.parse(input);
        System.out.println("sites:");
        for(Map.Entry<String,SiteConfig> entry: sites.entrySet())
            System.out.println(entry.getKey() + ":\n" + entry.getValue() + "\n");

    }
}
