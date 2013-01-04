/*
 * Tigase Message Archiving Component
 * Copyright (C) 2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.archive;

import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;

import static tigase.archive.MessageArchivePlugin.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.conf.Configurable;
import tigase.db.UserRepository;
import tigase.xml.Element;
import tigase.xmpp.*;

/**
 *
 * @author andrzej
 */
public class MessageArchiveComponent extends AbstractMessageReceiver {
        
        private static final Logger log = Logger.getLogger(MessageArchiveComponent.class.getCanonicalName());
        
        private static final String MSG_ARCHIVE_REPO_URI_PROP_KEY = "archive-repo-uri";
        private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        
        private MessageArchiveDB msg_repo = new MessageArchiveDB();

        public MessageArchiveComponent() {
                super();
                
                setName("message-archive");
        }
        
        @Override
        public void processPacket(Packet packet) {
                if (packet.getStanzaTo() != null && !getComponentId().equals(packet.getStanzaTo())) {
                        storeMessage(packet);
                        return;
                }
                
                try {
                        try {
                                processActionPacket(packet);
                        }
                        catch (XMPPException ex) {
                                if (log.isLoggable(Level.WARNING)) {
                                        log.log(Level.WARNING, "internal server while processing packet = " + packet.toString(), ex);
                                }
                                        
                                addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, (String) null, true));                                
                        }
                } catch (PacketErrorTypeException ex) {
                        if (log.isLoggable(Level.FINEST)) {
                                log.log(Level.FINEST, "error with packet in error state - ignoring packet = {0}", packet);
                        }
                }
        }        
        
        protected void processActionPacket(Packet packet) throws PacketErrorTypeException, XMPPException {
                for (Element child : packet.getElement().getChildren()) {                
                        if (child.getName() == "list") {
                                switch (packet.getType()) {
                                        case get:
                                                listCollections(packet, child);
                                                break;

                                        default:
                                                addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Request type is incorrect", false));
                                                break;
                                }
                        }
                        else if (child.getName() == "retrieve") {
                                switch (packet.getType()) {
                                        case get:
                                                getMessages(packet, child);
                                                break;

                                        default:
                                                addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Request type is incorrect", false));
                                                break;
                                }
                        }
                        else if (child.getName() == "remove") {
                                switch (packet.getType()) {
                                        case set:
                                                removeMessages(packet, child);
                                                break;

                                        default:
                                                addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Request type is incorrect", false));
                                                break;
                                }
                        }
                }                
        }
        
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> defs = super.getDefaults(params);
		String db_uri = (String) params.get(Configurable.USER_REPO_URL_PROP_KEY);

                if (db_uri == null) {
                        db_uri = (String) params.get(Configurable.GEN_USER_DB_URI);
                }
                
		if (db_uri != null) {
			defs.put(MSG_ARCHIVE_REPO_URI_PROP_KEY, db_uri);
		}

		return defs;
	}

	@Override
	public String getDiscoDescription() {
		return "Message Archiving (XEP-0136) Support";
	}
        
        @Override
        public void setProperties(Map<String, Object> props) {
                try {
                        super.setProperties(props);
                        
                        if (props.size() == 1) {
                                return;
                        }
                        
                        Map<String,String> repoProps = new HashMap<String,String>(4);
                        for (Entry<String,Object> entry : props.entrySet()) {
                                if (entry.getKey() == null || entry.getValue() == null)
                                        continue;
                                
                                repoProps.put(entry.getKey(), entry.getValue().toString());
                        }
                        
                        String uri = (String) props.get(MSG_ARCHIVE_REPO_URI_PROP_KEY);
                        if (uri != null) {
                                msg_repo.initRepository(uri, repoProps);
                        }
                        else {
                                log.log(Level.SEVERE, "repository uri is NULL!");
                        }
                } catch (SQLException ex) {
                        log.log(Level.SEVERE, "error initializing repository", ex);
                }
        }
        
        private void storeMessage(Packet packet) {
                String ownerStr = packet.getAttribute(OWNER_JID);
                packet.getElement().removeAttribute(OWNER_JID);
                BareJID owner = BareJID.bareJIDInstanceNS(ownerStr);
                boolean outgoing = owner.equals(packet.getStanzaFrom().getBareJID());
                BareJID buddy = outgoing ? packet.getStanzaTo().getBareJID() : packet.getStanzaFrom().getBareJID();
                msg_repo.archiveMessage(owner, buddy, (short) (outgoing ? 0 : 1), packet.getElement());
        }
        
        private void listCollections(Packet packet, Element list) throws XMPPException {
                try {
                        RSM rsm = RSM.parseRootElement(list);
                        if (list.getAttribute("with") == null) {
                                addOutPacket(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, "Request parameter with must be specified", true));
                                return;
                        }

                        String with = list.getAttribute("with");
                        String startStr = list.getAttribute("start");
                        String stopStr = list.getAttribute("end");

                        if (rsm.getAfter() != null) {
                                Calendar cal = Calendar.getInstance();
                                Date tmp;

                                synchronized (formatter) {
                                        try {
                                                tmp = formatter.parse(rsm.getAfter());
                                        }
                                        catch (ParseException e) {
                                                addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Date parsing error", true));
                                                return;
                                        }
                                }

                                cal.setTime(tmp);
                                cal.add(Calendar.DAY_OF_MONTH, 1);

                                synchronized (formatter) {
                                        startStr = formatter.format(cal.getTime());
                                }
                        }

                        if (rsm.getBefore() != null) {
                                Calendar cal = Calendar.getInstance();
                                Date tmp;

                                synchronized (formatter) {
                                        try {
                                                tmp = formatter.parse(rsm.getBefore());
                                        }
                                        catch (ParseException e) {
                                                addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Date parsing error", true));
                                                return;
                                        }
                                }

                                cal.setTime(tmp);
                                cal.add(Calendar.DAY_OF_MONTH, -1);
                                synchronized (formatter) {
                                        stopStr = formatter.format(cal.getTime());
                                }
                        }

                        Date start = null;
                        Date stop = null;
                        synchronized (formatter) {
                                try {
                                        start = formatter.parse(startStr);
                                        stop = formatter.parse(stopStr);
                                }
                                catch (Exception ex) {
                                        log.log(Level.FINER, "datetime parsing format exception", ex);
                                }
                        }
                        
                        List<Element> chats = msg_repo.getCollections(packet.getStanzaFrom().getBareJID(), with, start, stop, rsm.getBefore() != null, rsm.getLimit());
                        Element retList = new Element(LIST);
                        retList.setXMLNS(XEP0136NS);

                        if (chats == null) {
                                addOutPacket(Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "No such collection", true));
                                return;
                        }
                        else if (!chats.isEmpty()) {
                                retList.addChildren(chats);
                                rsm.setResults(null, chats.get(0).getAttribute("start"), chats.get(chats.size() - 1).getAttribute("start"));

                                retList.addChild(rsm.toElement());
                                addOutPacket(packet.okResult(retList, 0));
                        }
                        else {
                                addOutPacket(Authorization.ITEM_NOT_FOUND.getResponseMessage(packet, "No items in specified period", true));
                        }
                }
                catch (SQLException e) {
                        log.log(Level.SEVERE, "Error listing collections", e);
                        addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Database error occured", true));
                }
        }

        private void getMessages(Packet packet, Element retrieve) throws XMPPException {
                try {
                        RSM rsm = RSM.parseRootElement(retrieve);//new RSM(retrieve.findChild("/retrieve/set"), 30);
                        int limit = rsm.getLimit() != null ? rsm.getLimit() : 100;
                        int offset = 0;
                        if (rsm.getAfter() != null) {
                                offset = Integer.parseInt(rsm.getAfter());
                        }
                        
                        List<Element> items = msg_repo.getItems(packet.getStanzaFrom().getBareJID(), retrieve.getAttribute("with"), retrieve.getAttribute("start"), limit, offset);
                        Element retList = new Element("chat", new String[]{"with", "start"}, new String[]{retrieve.getAttribute("with"), retrieve.getAttribute("start")});
                        retList.setXMLNS(XEP0136NS);

                        if (!items.isEmpty()) {
                                rsm = new RSM(null);
                                rsm.setResults(items.size(), String.valueOf(offset+1), String.valueOf(offset+items.size()));
                                retList.addChildren(items);
                                retList.addChild(rsm.toElement());
                        }

                        addOutPacket(packet.okResult(retList, 0));
                }
                catch (SQLException e) {
                        log.log(Level.SEVERE, "Error retrieving messages", e);
                        addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Database error occured", true));
                }
        }

        private void removeMessages(Packet packet, Element remove) throws XMPPException {
                if (remove.getAttribute("with") == null || remove.getAttribute("start") == null || remove.getAttribute("end") == null) {
                        addOutPacket(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, "Parameters with, start, end cannot be null", true));
                        return;
                }

                try {
                        String startStr = remove.getAttribute("start");
                        String stopStr = remove.getAttribute("end");
                        Date start = null;
                        Date stop = null;
                        synchronized (formatter) {
                                try {
                                        start = formatter.parse(startStr);
                                        stop = formatter.parse(stopStr);
                                }
                                catch (Exception ex) {
                                        log.log(Level.FINER, "datetime parsing format exception", ex);
                                }
                        }
                        msg_repo.removeItems(packet.getStanzaFrom().getBareJID(), remove.getAttribute("with"), start, stop);
                        addOutPacket(packet.okResult((Element) null, 0));
                }
                catch (SQLException e) {
                        log.log(Level.SEVERE, "Error removing messages", e);
                        addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Database error occured", true));
                }
        }
        
}