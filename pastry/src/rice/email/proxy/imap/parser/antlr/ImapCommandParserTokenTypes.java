// $ANTLR 2.7.2a2 (20020112-1): "grammer.g" -> "ImapCommandParser.java"$

package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;
import rice.email.proxy.imap.commands.search.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.InputBuffer;

import java.io.Reader;
import java.util.*;


public interface ImapCommandParserTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int CHECK = 4;
	int NOOP = 5;
	int LOGOUT = 6;
	int CAPABILITY = 7;
	int CREATE = 8;
	int DELETE = 9;
	int LIST = 10;
	int SUBSCRIBE = 11;
	int UNSUBSCRIBE = 12;
	int LSUB = 13;
	int EXAMINE = 14;
	int LOGIN = 15;
	int SELECT = 16;
	int FETCH = 17;
	int UID = 18;
	int APPEND = 19;
	int COPY = 20;
	int STORE = 21;
	int STATUS = 22;
	int EXPUNGE = 23;
	int CLOSE = 24;
	int BODY = 25;
	int RFC822 = 26;
	int PEEK = 27;
	int HEADER = 28;
	int FIELDS = 29;
	int NOT = 30;
	int TEXT = 31;
	int MIME = 32;
	int SIZE = 33;
	int ALL = 34;
	int FAST = 35;
	int FULL = 36;
	int BODYSTRUCTURE = 37;
	int ENVELOPE = 38;
	int FLAGS = 39;
	int INTERNALDATE = 40;
	int SEARCH = 41;
	int ANSWERED = 42;
	int BCC = 43;
	int BEFORE = 44;
	int CC = 45;
	int DELETED = 46;
	int DRAFT = 47;
	int FLAGGED = 48;
	int FROM = 49;
	int KEYWORD = 50;
	int LARGER = 51;
	int NEW = 52;
	int OLD = 53;
	int ON = 54;
	int OR = 55;
	int RECENT = 56;
	int SEEN = 57;
	int SENTBEFORE = 58;
	int SENTON = 59;
	int SENTSINCE = 60;
	int SINCE = 61;
	int SMALLER = 62;
	int SUBJECT = 63;
	int TO = 64;
	int UNANSWERED = 65;
	int UNDELETED = 66;
	int UNDRAFT = 67;
	int UNFLAGGED = 68;
	int UNKEYWORD = 69;
	int UNSEEN = 70;
	int PERIOD = 71;
	int SPACE = 72;
	int LPAREN = 73;
	int RPAREN = 74;
	int ATOM = 75;
	int FLAG = 76;
	int LSBRACKET = 77;
	int RSBRACKET = 78;
	int LSANGLE = 79;
	int RSANGLE = 80;
	int NUMBER = 81;
	int QUOTE = 82;
	int QUOTED_CHAR = 83;
	int STRING = 84;
	int QUOTED = 85;
	int QUOTED_SPECIALS = 86;
	int ATOM_CHAR = 87;
	int CHAR = 88;
	int CTL = 89;
	int PLUS = 90;
	int LITERAL_START = 91;
	int UNKNOWN = 92;
}
