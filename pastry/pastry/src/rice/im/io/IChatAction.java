//-----------------------------------------------------------------------------
// $RCSfile$
// $Revision$
// $Author$
// $Date$
//-----------------------------------------------------------------------------

package rice.im.io;
import javax.swing.*;

import rice.im.*;
///////////////////////////////////////////////////////////////////////////////
/** 
 * Actions returned by ChatApp.getAction() all implement this. 
 * @author David M. Johnson
 * @version $Revision$
 *
 * <p>The contents of this file are subject to the Mozilla Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/</p>
 * Original Code: Relay-JFC Chat Client <br>
 * Initial Developer: David M. Johnson <br>
 * Contributor(s): No contributors to this file <br>
 * Copyright (C) 1997-2000 by David M. Johnson <br>
 * All Rights Reserved.
 */
public interface IChatAction extends Action {

   /** Set enabled or disabled, depending on chat app state. */
   public void update();

   /** Get the actual action object. */
   public AbstractAction getActionObject();

   /** Set context for action. */
   public void setContext(Object context);

   /** Get context for action. */
   public Object getContext();
}
