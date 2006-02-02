/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Properties;

import java.util.Arrays;
import java.math.BigInteger;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.lang.reflect.Method;

import net.java.dev.spellcast.utilities.LockableListModel;
import net.java.dev.spellcast.utilities.SortedListModel;

/**
 * The main class for the <code>KoLmafia</code> package.  This
 * class encapsulates most of the data relevant to any given
 * session of <code>Kingdom of Loathing</code> and currently
 * functions as the blackboard in the architecture.  When data
 * listeners are implemented, it will continue to manage most
 * of the interactions.
 */

public abstract class KoLmafia implements KoLConstants
{
	protected static PrintStream logStream = NullStream.INSTANCE;
	protected static LimitedSizeChatBuffer commandBuffer = null;

	protected static final String [] trapperItemNames = { "yak skin", "penguin skin", "hippopotamus skin" };
	protected static final int [] trapperItemNumbers = { 394, 393, 395 };

	protected boolean isLoggingIn;
	protected boolean isMakingRequest;
	protected KoLRequest currentRequest;
	protected LoginRequest cachedLogin;

	protected String password, sessionID, passwordHash;

	protected KoLSettings settings;
	protected PrintStream macroStream;
	protected Properties LOCAL_SETTINGS = new Properties();

	protected int currentState;
	protected boolean permitContinue;

	protected int [] initialStats = new int[3];
	protected int [] fullStatGain = new int[3];

	protected SortedListModel saveStateNames = new SortedListModel();
	protected List recentEffects = new ArrayList();

	private static TreeMap seenPlayerIDs = new TreeMap();
	private static TreeMap seenPlayerNames = new TreeMap();
	protected SortedListModel contactList = new SortedListModel();

	protected SortedListModel tally = new SortedListModel();
	protected SortedListModel missingItems = new SortedListModel();
	protected SortedListModel hermitItems = new SortedListModel();
	protected SortedListModel hunterItems = new SortedListModel();
	protected LockableListModel restaurantItems = new LockableListModel();
	protected LockableListModel microbreweryItems = new LockableListModel();
	protected LockableListModel galaktikCures = new LockableListModel();

	protected boolean useDisjunction;
	protected SortedListModel conditions = new SortedListModel();
	protected LockableListModel adventureList = new LockableListModel();
	protected LockableListModel encounterList = new LockableListModel();

	/**
	 * The main method.  Currently, it instantiates a single instance
	 * of the <code>KoLmafiaGUI</code>.
	 */

	public static void main( String [] args )
	{
		boolean useGUI = true;
		for ( int i = 0; i < args.length; ++i )
		{
			if ( args[i].equals( "--CLI" ) )
				useGUI = false;
			if ( args[i].equals( "--GUI" ) )
				useGUI = true;
		}

		if ( useGUI )
			KoLmafiaGUI.main( args );
		else
			KoLmafiaCLI.main( args );
	}

	/**
	 * Constructs a new <code>KoLmafia</code> object.  All data fields
	 * are initialized to their default values, the global settings
	 * are loaded from disk.
	 */

	public KoLmafia()
	{
		this.isLoggingIn = true;
		this.useDisjunction = false;

		this.settings = GLOBAL_SETTINGS;
		this.macroStream = NullStream.INSTANCE;

		String [] currentNames = GLOBAL_SETTINGS.getProperty( "saveState" ).split( "//" );
		for ( int i = 0; i < currentNames.length; ++i )
			saveStateNames.add( currentNames[i] );

		// This line is added to clear out data from previous
		// releases of KoLmafia - the extra disk access does
		// affect performance, but not significantly.

		storeSaveStates();
		deinitialize();
	}

	/**
	 * Updates the currently active display in the <code>KoLmafia</code>
	 * session.
	 */

	public synchronized void updateDisplay( int state, String message )
	{
		if ( state != NORMAL_STATE )
			this.currentState = state;

		logStream.println( message );

		if ( commandBuffer != null )
		{
			StringBuffer colorBuffer = new StringBuffer();
			if ( state == ERROR_STATE )
				colorBuffer.append( "<font color=red>" );
			else
				colorBuffer.append( "<font color=black>" );

			colorBuffer.append( message.indexOf( LINE_BREAK ) != -1 ? ("<pre>" + message + "</pre>") : message );
			colorBuffer.append( "</font><br>" );
			colorBuffer.append( LINE_BREAK );

			commandBuffer.append( colorBuffer.toString() );
		}
	}

	public void enableDisplay()
	{
		int state =  permitsContinue() ? ENABLE_STATE : ERROR_STATE;
		updateDisplay( state, "" );
	}

	/**
	 * Initializes the <code>KoLmafia</code> session.  Called after
	 * the login has been confirmed to notify the client that the
	 * login was successful, the user-specific settings should be
	 * loaded, and the user can begin adventuring.
	 */

	public void initialize( String loginname, String sessionID, boolean getBreakfast )
	{
		// Initialize the variables to their initial
		// states to avoid null pointers getting thrown
		// all over the place

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		this.sessionID = sessionID;

		KoLCharacter.reset( loginname );
		KoLMailManager.reset();
		FamiliarData.reset();
		CharpaneRequest.reset();
		MushroomPlot.reset();
		StoreManager.reset();
		CakeArenaManager.reset();
		MuseumManager.reset();
		ClanManager.reset();

		this.recentEffects.clear();
		this.conditions.clear();
		this.missingItems.clear();

		this.encounterList.clear();
		this.adventureList.clear();

		this.hermitItems.clear();
		this.hunterItems.clear();
		this.restaurantItems.clear();
		this.microbreweryItems.clear();
		this.galaktikCures.clear();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Retrieve the items which are available for consumption
		// and item creation.

		(new EquipmentRequest( this, EquipmentRequest.CLOSET )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Get current moon phases

		(new MoonPhaseRequest( this )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Retrieve the player data -- just in
		// case adventures or HP changed.

		(new CharsheetRequest( this )).run();
		registerPlayer( loginname, String.valueOf( KoLCharacter.getUserID() ) );

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Retrieve campground data to see if the user is able to
		// cook, make drinks or make toast.

		updateDisplay( DISABLE_STATE, "Retrieving campground data..." );
		(new CampgroundRequest( this )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Retrieve the list of familiars which are available to
		// the player, if they haven't opted to skip them.

		(new FamiliarRequest( this )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		updateDisplay( DISABLE_STATE, "Retrieving account options..." );
		(new AccountRequest( this )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		updateDisplay( DISABLE_STATE, "Retrieving contact list..." );
		(new ContactListRequest( this )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		// Retrieve the list of outfits which are available to the
		// character.  Due to lots of bug reports, this is no longer
		// a skippable option.

		(new EquipmentRequest( this, EquipmentRequest.EQUIPMENT )).run();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		resetSession();

		applyRecentEffects();

		// Retrieve breakfast if the option to retrieve breakfast
		// was previously selected.

		if ( getBreakfast )
			getBreakfast();

		if ( !permitsContinue() )
		{
			deinitialize();
			return;
		}

		this.isLoggingIn = false;
		this.settings = new KoLSettings( loginname );
		resetContinueState();

		HPRestoreItemList.reset();
		MPRestoreItemList.reset();
		KoLCharacter.refreshCalculatedLists();
	}

	/**
	 * Utility method used to notify the client that it should attempt
	 * to retrieve breakfast.
	 */

	public void getBreakfast()
	{
		updateDisplay( DISABLE_STATE, "Retrieving breakfast..." );

		if ( KoLCharacter.hasToaster() )
			for ( int i = 0; i < 3 && permitsContinue(); ++i )
				(new CampgroundRequest( this, "toast" )).run();

		resetContinueState();

		if ( KoLCharacter.hasArches() )
			(new CampgroundRequest( this, "arches" )).run();

		resetContinueState();

		if ( KoLCharacter.canSummonSnowcones() )
			getBreakfast( "Summon Snowcone", 1 );

		resetContinueState();

		if ( KoLCharacter.canSummonReagent() )
			getBreakfast( "Advanced Saucecrafting", 3 );

		resetContinueState();

		if ( KoLCharacter.canEat() && KoLCharacter.canSummonNoodles() )
			getBreakfast( "Pastamastery", 3 );

		resetContinueState();

		if ( KoLCharacter.canDrink() && KoLCharacter.canSummonShore() )
			getBreakfast( "Advanced Cocktailcrafting", 3 );

		resetContinueState();

		updateDisplay( ENABLE_STATE, "Breakfast retrieved." );
	}

	public void getBreakfast( String skillname, int standardCast )
	{
		int consumptionPerCast = ClassSkillsDatabase.getMPConsumptionByID( ClassSkillsDatabase.getSkillID( skillname ) );
		if ( consumptionPerCast != 0 && consumptionPerCast <= KoLCharacter.getCurrentMP() )
			(new UseSkillRequest( this, skillname, "", Math.min( standardCast, KoLCharacter.getCurrentMP() / consumptionPerCast ) )).run();
	}

	/**
	 * Deinitializes the <code>KoLmafia</code> session.  Called after
	 * the user has logged out.
	 */

	public void deinitialize()
	{
		sessionID = null;
		passwordHash = null;
		cachedLogin = null;

		cancelRequest();
		closeMacroStream();
	}

	/**
	 * Used to reset the session tally to its original values.
	 */

	public void resetSession()
	{
		tally.clear();

		initialStats[0] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMuscle() );
		initialStats[1] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMysticality() );
		initialStats[2] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMoxie() );

		fullStatGain[0] = 0;
		fullStatGain[1] = 0;
		fullStatGain[2] = 0;

		processResult( new AdventureResult( AdventureResult.MEAT ) );
		processResult( new AdventureResult( AdventureResult.SUBSTATS ) );
		processResult( new AdventureResult( AdventureResult.DIVIDER ) );
	}

	/**
	 * Utility method to parse an individual adventuring result.
	 * This method determines what the result actually was and
	 * adds it to the tally.
	 *
	 * @param	result	String to parse for the result
	 */

	public void parseResult( String result )
	{
		String trimResult = result.trim();

		// Because of the simplified parsing, there's a chance that
		// the "gain" acquired wasn't a subpoint (in other words, it
		// includes the word "a" or "some"), which causes a NFE or
		// possibly a ParseException to be thrown.  catch them and
		// do nothing (eventhough it's technically bad style).

		if ( trimResult.startsWith( "You gain a" ) || trimResult.startsWith( "You gain some" ) )
			return;

		try
		{
			if ( logStream != null )
				logStream.println( "Parsing result: " + trimResult );

			processResult( AdventureResult.parseResult( trimResult ) );
		}
		catch ( Exception e )
		{
			e.printStackTrace( logStream );
			e.printStackTrace();
		}
	}

	public void parseItem( String result )
	{
		if ( logStream != null )
			logStream.println( "Parsing item: " + result );

		// We do the following in order to not get confused by:
		//
		// Frobozz Real-Estate Company Instant House (TM)
		// stone tablet (Sinister Strumming)
		// stone tablet (Squeezings of Woe)
		// stone tablet (Really Evil Rhythm)
		//
		// which otherwise cause an exception and a stack trace

		// Look for a verbatim match
		int itemID = TradeableItemDatabase.getItemID( result.trim() );
		if ( itemID != -1 )
		{
			processResult( new AdventureResult( itemID, 1 ) );
			return;
		 }

		// Remove parenthesized number and match again.
		StringTokenizer parsedItem = new StringTokenizer( result, "()" );
		String name = parsedItem.nextToken().trim();
		int count = 1;

		if ( parsedItem.hasMoreTokens() )
		{
			try
			{
				count = df.parse( parsedItem.nextToken() ).intValue();
			}
			catch ( Exception e )
			{
				e.printStackTrace( logStream );
				e.printStackTrace();
				return;
			}
		}

		processResult( new AdventureResult( name, count, false ) );
	}

	public void parseEffect( String result )
	{
		if ( logStream != null )
			logStream.println( "Parsing effect: " + result );

		StringTokenizer parsedEffect = new StringTokenizer( result, "()" );
		String parsedEffectName = parsedEffect.nextToken().trim();
		String parsedDuration = parsedEffect.hasMoreTokens() ? parsedEffect.nextToken() : "1";

		try
		{
			processResult( new AdventureResult( parsedEffectName, df.parse( parsedDuration ).intValue(), true ) );
		}
		catch ( Exception e )
		{
			e.printStackTrace( logStream );
			e.printStackTrace();
		}
	}

	/**
	 * Utility method used to process a result.  By default, this
	 * method will also add an adventure result to the tally directly.
	 * This is used whenever the nature of the result is already known
	 * and no additional parsing is needed.
	 *
	 * @param	result	Result to add to the running tally of adventure results
	 */

	public void processResult( AdventureResult result )
	{	processResult( result, true );
	}

	/**
	 * Utility method used to process a result, and the user wishes to
	 * specify whether or not the result should be added to the running
	 * tally.  This is used whenever the nature of the result is already
	 * known and no additional parsing is needed.
	 *
	 * @param	result	Result to add to the running tally of adventure results
	 * @param	shouldTally	Whether or not the result should be added to the running tally
	 */

	public void processResult( AdventureResult result, boolean shouldTally )
	{
		// This should not happen, but check just in case and
		// return if the result was null.

		if ( result == null )
			return;

		if ( logStream != null )
			logStream.println( "Processing result: " + result );

		String resultName = result.getName();

		// This should not happen, but check just in case and
		// return if the result name was null.

		if ( resultName == null )
			return;

		// Process the adventure result in this section; if
		// it's a status effect, then add it to the recent
		// effect list.  Otherwise, add it to the tally.

		if ( result.isStatusEffect() )
			AdventureResult.addResultToList( recentEffects, result );
		else if ( result.isItem() || resultName.equals( AdventureResult.SUBSTATS ) || resultName.equals( AdventureResult.MEAT ) )
		{
			if ( shouldTally )
				AdventureResult.addResultToList( tally, result );
		}

		KoLCharacter.processResult( result );

		if ( !shouldTally )
			return;

		// Now, if it's an actual stat gain, be sure to update the
		// list to reflect the current value of stats so far.

		if ( resultName.equals( AdventureResult.SUBSTATS ) && tally.size() >= 2 )
		{
			fullStatGain[0] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMuscle() ) - initialStats[0];
			fullStatGain[1] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMysticality() ) - initialStats[1];
			fullStatGain[2] = KoLCharacter.calculateBasePoints( KoLCharacter.getTotalMoxie() ) - initialStats[2];

			if ( tally.size() > 2 )
				tally.set( 2, new AdventureResult( AdventureResult.FULLSTATS, fullStatGain ) );
			else
				tally.add( new AdventureResult( AdventureResult.FULLSTATS, fullStatGain ) );
		}

		// Process the adventure result through the conditions
		// list, removing it if the condition is satisfied.

		int conditionsIndex = conditions.indexOf( result );

		if ( !resultName.equals( AdventureResult.ADV ) && conditionsIndex != -1 )
		{
			if ( resultName.equals( AdventureResult.SUBSTATS ) )
			{
				// If the condition is a substat condition,
				// then zero out the appropriate count, if
				// applicable, and remove the substat condition
				// if the overall count dropped to zero.

				AdventureResult condition = (AdventureResult) conditions.get( conditionsIndex );

				int [] substats = new int[3];
				for ( int i = 0; i < 3; ++i )
					substats[i] = Math.max( 0, condition.getCount(i) - result.getCount(i) );

				condition = new AdventureResult( AdventureResult.SUBSTATS, substats );

				if ( condition.getCount() == 0 )
					conditions.remove( conditionsIndex );
				else
					conditions.set( conditionsIndex, condition );
			}
			else if ( result.getCount( conditions ) <= result.getCount() )
			{
				// If this results in the satisfaction of a
				// condition, then remove it.

				conditions.remove( conditionsIndex );
			}
			else
			{
				// Otherwise, this was a partial satisfaction
				// of a condition.  Decrement the count by the
				// negation of this result.

				AdventureResult.addResultToList( conditions, result.getNegation() );
			}
		}
	}

	/**
	 * Adds the recent effects accumulated so far to the actual effects.
	 * This should be called after the previous effects were decremented,
	 * if adventuring took place.
	 */

	public void applyRecentEffects()
	{
		for ( int j = 0; j < recentEffects.size(); ++j )
			AdventureResult.addResultToList( KoLCharacter.getEffects(), (AdventureResult) recentEffects.get(j) );
		KoLCharacter.sortEffects();

		recentEffects.clear();
		FamiliarData.updateWeightModifier();
	}

	/**
	 * Returns the string form of the player ID associated
	 * with the given player name.
	 *
	 * @param	playerID	The ID of the player
	 * @return	The player's name if it has been seen, or null if it has not
	 *          yet appeared in the chat (not likely, but possible).
	 */

	public static String getPlayerName( String playerID )
	{	return (String) seenPlayerNames.get( playerID );
	}

	/**
	 * Returns the string form of the player ID associated
	 * with the given player name.
	 *
	 * @param	playerName	The name of the player
	 * @return	The player's ID if the player has been seen, or the player's name
	 *			with spaces replaced with underscores and other elements encoded
	 *			if the player's ID has not been seen.
	 */

	public static String getPlayerID( String playerName )
	{
		if ( playerName == null )
			return null;

		String playerID = (String) seenPlayerIDs.get( playerName.toLowerCase() );
		return playerID != null ? playerID : playerName;
	}

	/**
	 * Registers the given player name and player ID with
	 * KoLmafia's player name tracker.
	 *
	 * @param	playerName	The name of the player
	 * @param	playerID	The player ID associated with this player
	 */

	public static void registerPlayer( String playerName, String playerID )
	{
		if ( !seenPlayerIDs.containsKey( playerName.toLowerCase() ) )
		{
			seenPlayerIDs.put( playerName.toLowerCase(), playerID );
			seenPlayerNames.put( playerID, playerName );
		}
	}

	public void registerContact( String playerName, String playerID )
	{
		registerPlayer( playerName, playerID );
		if ( !contactList.contains( playerName ) )
			contactList.add( playerName.toLowerCase() );
	}

	/**
	 * Retrieves the session ID for this <code>KoLmafia</code> session.
	 * @return	The session ID of the current session
	 */

	public String getSessionID()
	{	return sessionID;
	}

	/**
	 * Stores the password hash for this <code>KoLmafia</code> session.
	 * @param	passwordHash	The password hash for this session
	 */

	public void setPasswordHash( String passwordHash )
	{	this.passwordHash = passwordHash;
	}

	/**
	 * Retrieves the password hash for this <code>KoLmafia</code> session.
	 * @return	The password hash of the current session
	 */

	public String getPasswordHash()
	{	return passwordHash;
	}

	/**
	 * Returns the character's contact list.
	 */

	public SortedListModel getContactList()
	{	return contactList;
	}

	/**
	 * Returns the list of items which are available from the hermit today.
	 */

	public SortedListModel getHermitItems()
	{	return hermitItems;
	}

	/**
	 * Returns the list of items which are available from the
	 * bounty hunter hunter today.
	 */

	public SortedListModel getBountyHunterItems()
	{	return hunterItems;
	}

	/**
	 * Returns the list of items which are available from
	 * Chez Snootee today.
	 */

	public LockableListModel getRestaurantItems()
	{	return restaurantItems;
	}

	/**
	 * Returns the list of items which are available from the
	 * Gnomish Micromicrobrewery today.
	 */

	public LockableListModel getMicrobreweryItems()
	{	return microbreweryItems;
	}

	/**
	 * Returns the list of cures which are currently available from
	 * Doc Galaktik
	 */

	public LockableListModel getGalaktikCures()
	{	return galaktikCures;
	}

	/**
	 * Returns whether or not the current user has a ten-leaf clover.
	 *
	 * @return	<code>true</code>
	 */

	public boolean isLuckyCharacter()
	{	return KoLCharacter.getInventory().contains( SewerRequest.CLOVER );
	}

	/**
	 * Utility method which ensures that the amount needed exists,
	 * and if not, calls the appropriate scripts to do so.
	 */

	private final boolean recover( int needed, String currentName, String maximumName, String scriptProperty, String listProperty, Class techniqueList )
	{
		// Do not attempt recovery if the person has an
		// auto-halting script and they are below the
		// needed health.

		int haltTolerance = (int)( Double.parseDouble( StaticEntity.getProperty( "battleStop" ) ) * (double) KoLCharacter.getMaximumHP() );
		if ( haltTolerance != 0 && KoLCharacter.getCurrentHP() <= haltTolerance )
			return false;

		try
		{
			Object [] empty = new Object[0];
			Method currentMethod, maximumMethod;

			currentMethod = KoLCharacter.class.getMethod( currentName, new Class[0] );
			maximumMethod = KoLCharacter.class.getMethod( maximumName, new Class[0] );

			if ( ((Number)currentMethod.invoke( null, empty )).intValue() >= needed )
				return true;

			int current = -1;
			resetContinueState();

			// First, attempt to recover using the appropriate script, if it exists.
			// This uses a lot of excessive reflection, but the idea is that it
			// checks the current value of the stat against the needed value of
			// the stat and makes sure that there's a change with every iteration.
			// If there is no change, it exists the loop.

			String scriptPath = settings.getProperty( scriptProperty );
			if ( !scriptPath.trim().equals( "" ) )
			{
				while ( permitsContinue() && ((Number)currentMethod.invoke( null, empty )).intValue() < needed &&
					current != ((Number)currentMethod.invoke( null, empty )).intValue() )
				{
					current = ((Number)currentMethod.invoke( null, empty )).intValue();
					DEFAULT_SHELL.executeLine( scriptPath );
				}
			}
			else
			{
				// If it gets this far, then you should attempt to recover
				// using the selected items.  This involves a few extra
				// reflection methods.

				String restoreSetting = settings.getProperty( listProperty );

				int totalRestores = ((Number)techniqueList.getMethod( "size", new Class[0] ).invoke( null, empty )).intValue();
				Method getMethod = techniqueList.getMethod( "get", new Class [] { Integer.TYPE } );

				// Iterate through every single restore item, checking to
				// see if the settings wish to use this item.  If so, go ahead
				// and process the item's usage.

				Object currentTechnique;

				for ( int i = 0; i < totalRestores; ++i )
				{
					currentTechnique = getMethod.invoke( null, new Integer [] { new Integer(i) } );
					if ( restoreSetting.indexOf( currentTechnique.toString() ) != -1 )
					{
						current = -1;
						while ( ((Number)currentMethod.invoke( null, empty )).intValue() < needed &&
							current != ((Number)currentMethod.invoke( null, empty )).intValue() )
						{
							current = ((Number)currentMethod.invoke( null, empty )).intValue();
							resetContinueState();
							recoverOnce( currentTechnique );
						}
					}
				}
			}

			// Fall-through check, just in case you've reached the
			// desired value.

			if ( ((Number)currentMethod.invoke( null, empty )).intValue() >= needed )
			{
				updateDisplay( DISABLE_STATE, "Recovery complete.  Resuming requests..." );
				resetContinueState();
				return true;
			}

			// Now you know for certain that you did not reach the
			// desired value.  Report an error message.

			updateDisplay( ERROR_STATE, "Auto-recovery failed." );
			cancelRequest();
			return false;
		}
		catch ( Exception e )
		{
			cancelRequest();

			e.printStackTrace( logStream );
			e.printStackTrace();

			return false;
		}
	}

	/**
	 * Utility method called inbetween battles.  This method
	 * checks to see if the character's HP has dropped below
	 * the tolerance value, and recovers if it has (if
	 * the user has specified this in their settings).
	 */

	protected final boolean recoverHP()
	{
		double recover = Double.parseDouble( settings.getProperty( "hpAutoRecover" ) ) * (double) KoLCharacter.getMaximumHP();
		return recoverHP( (int) recover );
	}

	public final boolean recoverHP( int recover )
	{	return recover( recover, "getCurrentHP", "getMaximumHP", "hpRecoveryScript", "hpRestoreItems", HPRestoreItemList.class );
	}

	/**
	 * Utility method which uses the given recovery technique (not specified
	 * in a script) in order to restore.
	 */

	private final void recoverOnce( Object technique )
	{
		if ( technique == null )
			return;

		if ( technique == MPRestoreItemList.BEANBAG || technique == MPRestoreItemList.HOUSE || technique == HPRestoreItemList.HOUSE )
		{
			if ( KoLCharacter.getAdventuresLeft() == 0 )
				return;
		}
		else if ( technique != MPRestoreItemList.GALAKTIK && technique != HPRestoreItemList.GALAKTIK )
		{
			if ( !KoLCharacter.getInventory().contains( new AdventureResult( technique.toString(), 0 ) ) )
				return;
		}

		if ( technique instanceof HPRestoreItemList.HPRestoreItem )
			((HPRestoreItemList.HPRestoreItem)technique).recoverHP();

		if ( technique instanceof MPRestoreItemList.MPRestoreItem )
			((MPRestoreItemList.MPRestoreItem)technique).recoverMP();
	}

	/**
	 * Returns the total number of mana restores currently
	 * available to the player.
	 */

	public int getRestoreCount()
	{
		int restoreCount = 0;
		String mpRestoreSetting = settings.getProperty( "buffBotMPRestore" );

		for ( int i = 0; i < MPRestoreItemList.size(); ++i )
			if ( mpRestoreSetting.indexOf( MPRestoreItemList.get(i).toString() ) != -1 )
				restoreCount += MPRestoreItemList.get(i).getItem().getCount( KoLCharacter.getInventory() );

		return restoreCount;
	}

	/**
	 * Utility method called inbetween commands.  This method
	 * checks to see if the character's MP has dropped below
	 * the tolerance value, and recovers if it has (if
	 * the user has specified this in their settings).
	 */

	protected final boolean recoverMP()
	{
		double mpNeeded = Double.parseDouble( settings.getProperty( "mpAutoRecover" ) ) * (double) KoLCharacter.getMaximumMP();
		return recoverMP( (int) mpNeeded );
	}

	/**
	 * Utility method which restores the character's current
	 * mana points above the given value.
	 */

	public final boolean recoverMP( int mpNeeded )
	{	return recover( mpNeeded, "getCurrentMP", "getMaximumMP", "mpRecoveryScript", "buffBotMPRestore", MPRestoreItemList.class );
	}

	/**
	 * Utility method used to process the results of any adventure
	 * in the Kingdom of Loathing.  This method searches for items,
	 * stat gains, and losses within the provided string.
	 *
	 * @param	results	The string containing the results of the adventure
	 * @return	<code>true</code> if any results existed
	 */

	public boolean processResults( String results )
	{
		boolean hadResults = false;
		logStream.println( "Processing results..." );

		if ( results.indexOf( "gains a pound" ) != -1 )
		{
			KoLCharacter.incrementFamilarWeight();
			hadResults = true;
		}

		String plainTextResult = results.replaceAll( "<.*?>", "\n" );
		StringTokenizer parsedResults = new StringTokenizer( plainTextResult, "\n" );
		String lastToken = null;

		Matcher damageMatcher = Pattern.compile( "you for ([\\d,]+) damage" ).matcher( plainTextResult );
		int lastDamageIndex = 0;

		while ( damageMatcher.find( lastDamageIndex ) )
		{
			lastDamageIndex = damageMatcher.end();
			parseResult( "You lose " + damageMatcher.group(1) + " hit points" );
			hadResults = true;
		}

		damageMatcher = Pattern.compile( "You drop .*? ([\\d,]+) damage" ).matcher( plainTextResult );
		lastDamageIndex = 0;

		while ( damageMatcher.find( lastDamageIndex ) )
		{
			lastDamageIndex = damageMatcher.end();
			parseResult( "You lose " + damageMatcher.group(1) + " hit points" );
			hadResults = true;
		}

		while ( parsedResults.hasMoreTokens() )
		{
			lastToken = parsedResults.nextToken();

			// Skip effect acquisition - it's followed by a boldface
			// which makes the parser think it's found an item.

			if ( lastToken.startsWith( "You acquire" ) )
			{
				hadResults = true;
				if ( lastToken.indexOf( "effect" ) == -1 )
				{
					String item = parsedResults.nextToken();

					if ( lastToken.indexOf( "an item" ) != -1 )
						parseItem( item );
					else
					{
						// The name of the item follows the number
						// that appears after the first index.

						String countString = item.split( " " )[0];
						String itemName = item.substring( item.indexOf( " " ) ).trim();
						boolean isNumeric = true;

						for ( int i = 0; isNumeric && i < countString.length(); ++i )
							isNumeric &= Character.isDigit( countString.charAt(i) ) || countString.charAt(i) == ',';

						if ( !isNumeric )
							countString = "1";
						else if ( itemName.equals( "evil golden arches" ) )
							itemName = "evil golden arch";

						parseItem( itemName + " (" + countString + ")" );
					}
				}
				else
				{
					String effectName = parsedResults.nextToken();
					lastToken = parsedResults.nextToken();

					if ( lastToken.indexOf( "duration" ) == -1 )
						parseEffect( effectName );
					else
					{
						String duration = lastToken.substring( 11, lastToken.length() - 11 ).trim();
						parseEffect( effectName + " (" + duration + ")" );
					}
				}
			}
			else if ( (lastToken.startsWith( "You gain" ) || lastToken.startsWith( "You lose " )) )
			{
				hadResults = true;
				parseResult( lastToken.indexOf( "." ) == -1 ? lastToken : lastToken.substring( 0, lastToken.indexOf( "." ) ) );
			}
		}

		return hadResults;
	}

	/**
	 * Makes the given request for the given number of iterations,
	 * or until continues are no longer possible, either through
	 * user cancellation or something occuring which prevents the
	 * requests from resuming.
	 *
	 * @param	request	The request made by the user
	 * @param	iterations	The number of times the request should be repeated
	 */

	public void makeRequest( Runnable request, int iterations )
	{
		try
		{
			macroStream.print( KoLmafiaCLI.deriveCommand( request, iterations ) );

			// Handle the gym, which is the only adventure type
			// which needs to be specially handled.

			if ( request instanceof KoLAdventure )
			{
				KoLAdventure adventure = (KoLAdventure) request;
				if ( adventure.getFormSource().equals( "clan_gym.php" ) )
				{
					(new ClanGymRequest( this, Integer.parseInt( adventure.getAdventureID() ), iterations )).run();
					return;
				}
			}

			int currentEffectCount = KoLCharacter.getEffects().size();

			boolean pulledOver = false;
			boolean shouldRefreshStatus;

			// Otherwise, you're handling a standard adventure.  Be
			// sure to check to see if you're allowed to continue
			// after drunkenness.

			if ( KoLCharacter.isFallingDown() )
			{
				if ( request instanceof KoLAdventure && !(((KoLAdventure)request).getRequest() instanceof CampgroundRequest) && !confirmDrunkenRequest() )
					cancelRequest();

				pulledOver = true;
			}

			// Check to see if there are any end conditions.  If
			// there are conditions, be sure that they are checked
			// during the iterations.

			int initialConditions = conditions.size();
			int remainingConditions = initialConditions;

			// If this is an adventure request, make sure that it
			// gets validated before running.

			if ( request instanceof KoLAdventure )
			{
				// Validate the adventure
				AdventureDatabase.validateAdventure( (KoLAdventure) request );
			}

			// Begin the adventuring process, or the request execution
			// process (whichever is applicable).

			int currentIteration;

			for ( currentIteration = 1; permitsContinue() && currentIteration <= iterations; ++currentIteration )
			{
				// If the conditions existed and have been satisfied,
				// then you should stop.

				if ( conditions.size() < remainingConditions )
				{
					if ( conditions.size() == 0 || useDisjunction )
					{
						conditions.clear();
						remainingConditions = 0;
						break;
					}
				}

				remainingConditions = conditions.size();

				// Otherwise, disable the display and update the user
				// and the current request number.  Different requests
				// have different displays.  They are handled here.

				if ( request instanceof KoLAdventure )
					updateDisplay( DISABLE_STATE, "Request " + currentIteration + " of " + iterations + " (" + request.toString() + ") in progress..." );

				else if ( request instanceof ConsumeItemRequest )
				{
					int consumptionType = ((ConsumeItemRequest)request).getConsumptionType();
					String useTypeAsString = (consumptionType == ConsumeItemRequest.CONSUME_EAT) ? "Eating" :
						(consumptionType == ConsumeItemRequest.CONSUME_DRINK) ? "Drinking" : "Using";

					if ( iterations == 1 )
						updateDisplay( DISABLE_STATE, useTypeAsString + " " + ((ConsumeItemRequest)request).getItemUsed().toString() + "..." );
					else
						updateDisplay( DISABLE_STATE, useTypeAsString + " " + ((ConsumeItemRequest)request).getItemUsed().getName() + " (" + currentIteration + " of " + iterations + ")..." );
				}

				request.run();
				applyRecentEffects();

				// Prevent drunkenness adventures from occurring by
				// testing inebriety levels after the request is run.

				if ( request instanceof KoLAdventure && KoLCharacter.isFallingDown() && !pulledOver )
				{
					if ( permitsContinue() && !confirmDrunkenRequest() )
						cancelRequest();

					pulledOver = true;
				}

				shouldRefreshStatus = currentEffectCount != KoLCharacter.getEffects().size();

				// If this is a KoLRequest, make sure to process
				// any applicable adventure usage.

				if ( request instanceof KoLRequest )
				{
					int adventures = ((KoLRequest)request).getAdventuresUsed();
					if ( adventures > 0 )
						processResult( new AdventureResult( AdventureResult.ADV, 0 - adventures ) );
				}

				// One circumstance where you need a refresh is if
				// you gain/lose a status effect.

				shouldRefreshStatus |= currentEffectCount != KoLCharacter.getEffects().size();
				currentEffectCount = KoLCharacter.getEffects().size();

				// Another instance is if the player's equipment
				// results in recovery.

				shouldRefreshStatus |= request instanceof KoLAdventure && KoLCharacter.hasRecoveringEquipment();

				// If it turns out that you need to refresh the player's
				// status, go ahead and refresh it.

				if ( shouldRefreshStatus )
					(new CharpaneRequest( this )).run();
			}

			// If you've completed the requests, make sure to update
			// the display.

			if ( currentState != ERROR_STATE )
			{
				if ( !permitsContinue() )
				{
					// Special processing for adventures.

					if ( request instanceof KoLAdventure )
					{
						// If we canceled the iteration without
						// generating a real error, permit
						// scripts to continue.

						resetContinueState();
						updateDisplay( NORMAL_STATE, "Nothing more to do here." );
					}
				}
				else if ( remainingConditions != 0 )
					updateDisplay( NORMAL_STATE, "Requests completed!  (Conditions not yet met)" );

				else if ( initialConditions != 0 )
					updateDisplay( NORMAL_STATE, "Conditions satisfied after " + (currentIteration - 1) +
						((currentIteration == 2) ? " request." : " requests.") );

				else if ( currentIteration >= iterations )
					updateDisplay( NORMAL_STATE, "Requests completed!" );
			}
		}
		catch ( RuntimeException e )
		{
			// In the event that an exception occurs during the
			// request processing, catch it here, print it to
			// the logger (whatever it may be), and notify the
			// user that an error was encountered.

			e.printStackTrace( logStream );
			e.printStackTrace();

			updateDisplay( ERROR_STATE, "Unexpected error." );
		}
	}

	/**
	 * Removes the effects which are removed through a tiny house.
	 * This checks each status effect and checks the database to
	 * see if a tiny house will remove it.
	 */

	public void applyTinyHouseEffect()
	{
		Object [] effects = KoLCharacter.getEffects().toArray();
		AdventureResult currentEffect;

		for ( int i = effects.length - 1; i >= 0; --i )
		{
			currentEffect = (AdventureResult) effects[i];
			if ( StatusEffectDatabase.isTinyHouseClearable( currentEffect.getName() ) )
				KoLCharacter.getEffects().remove(i);
		}
	}

	/**
	 * Makes a request which attempts to remove the given effect.
	 */

	public abstract void makeUneffectRequest();

	/**
	 * Makes a request which attempts to zap a chosen item
	 */

	public abstract void makeZapRequest();

	/**
	 * Makes a request to the hermit in order to trade worthless
	 * items for more useful items.
	 */

	public abstract void makeHermitRequest();

	/**
	 * Makes a request to the trapper to trade yeti furs for
	 * other kinds of furs.
	 */

	public abstract void makeTrapperRequest();
	/**
	 * Makes a request to the hunter to trade today's bounty
	 * items in for meat.
	 */

	public abstract void makeHunterRequest();

	/**
	 * Makes a request to the untinkerer to untinker items
	 * into their component parts.
	 */

	public abstract void makeUntinkerRequest();

	/**
	 * Makes a request to set the mind control device to the desired value
	 */

	public abstract void makeMindControlRequest();

	/**
	 * Completes the infamous tavern quest.
	 */

	public void locateTavernFaucet()
	{
		if ( KoLCharacter.getLevel() < 3 )
		{
			updateDisplay( ERROR_STATE, "You need to level up first." );
			return;
		}

		// Make sure that you have the quest, and once you
		// do, go ahead and search for the faucet.

		if ( KoLCharacter.hasAccomplishment( KoLCharacter.TAVERN ) )
		{
			updateDisplay( ERROR_STATE, "You have already completed this quest." );
			return;
		}

		resetContinueState();
		(new KoLRequest( this, "council.php", true )).run();
		updateDisplay( DISABLE_STATE, "Searching for faucet..." );

		KoLAdventure adventure = new KoLAdventure( this, "", "rats.php", "", "Typical Tavern (Pre-Rat)" );
		adventure.run();

		ArrayList searchList = new ArrayList();
		for ( int i = 1; i <= 25; ++i )
			searchList.add( new Integer(i) );

		Integer searchIndex = new Integer(0);

		// Random guess instead of straightforward search
		// for the location of the faucet (lowers the chance
		// of bad results if the faucet is near the end).

		while ( !searchList.isEmpty() && KoLCharacter.getCurrentHP() > 0 &&
			(adventure.getRequest().responseText == null || adventure.getRequest().responseText.indexOf( "faucetoff" ) == -1) )
		{
			searchIndex = (Integer) searchList.get( RNG.nextInt( searchList.size() ) );
			searchList.remove( searchIndex );

			adventure.getRequest().addFormField( "where", searchIndex.toString() );
			adventure.run();
			resetContinueState();
		}

		// If you successfully find the location of the
		// rat faucet, then you've got it.

		if ( KoLCharacter.getCurrentHP() > 0 )
		{
			KoLCharacter.processResult( new AdventureResult( AdventureResult.ADV, 1 ) );
			int row = (int) ((searchIndex.intValue() - 1) / 5) + 1;
			int column = (searchIndex.intValue() - 1) % 5 + 1;
			updateDisplay( ENABLE_STATE, "Faucet found in row " + row + ", column " + column );
		}
	}

	/**
	 * Trades items with the guardian of the goud.
	 */

	public void tradeGourdItems()
	{
		updateDisplay( DISABLE_STATE, "Determining items needed..." );
		KoLRequest request = new KoLRequest( this, "town_right.php?place=gourd", true );
		request.run();

		// For every class, it's the same -- the message reads, "Bring back"
		// and then the number of the item needed.  Compare how many you need
		// with how many you have.

		Matcher neededMatcher = Pattern.compile( "Bring back (\\d+)" ).matcher( request.responseText );
		AdventureResult item;

		switch ( KoLCharacter.getPrimeIndex() )
		{
			case 0:
				item = new AdventureResult( 747, 5 );
				break;
			case 1:
				item = new AdventureResult( 559, 5 );
				break;
			default:
				item = new AdventureResult( 27, 5 );
		}

		int neededCount = neededMatcher.find() ? Integer.parseInt( neededMatcher.group(1) ) : 26;

		while ( neededCount <= 25 && neededCount <= item.getCount( KoLCharacter.getInventory() ) )
		{
			updateDisplay( DISABLE_STATE, "Giving up " + neededCount + " " + item.getName() + "s..." );
			request = new KoLRequest( this, "town_right.php?place=gourd&action=gourd", true );
			request.run();

			processResult( item.getInstance( 0 - neededCount++ ) );
		}

		int totalProvided = 0;
		for ( int i = 5; i < neededCount; ++i )
			totalProvided += i;

		updateDisplay( ENABLE_STATE, "Gourd trading complete (" + totalProvided + " " + item.getName() + "s given so far)." );
	}

	public void unlockGuildStore()
	{
		// Refresh the player's stats in order to get current
		// stat values to see if the quests can be completed.

		(new CharsheetRequest( this )).run();

		int baseStatValue = 0;
		int totalStatValue = 0;

		switch ( KoLCharacter.getPrimeIndex() )
		{
			case 0:
				baseStatValue = KoLCharacter.getBaseMuscle();
				totalStatValue = baseStatValue + KoLCharacter.getAdjustedMuscle();
				break;

			case 1:
				baseStatValue = KoLCharacter.getBaseMysticality();
				totalStatValue = baseStatValue + KoLCharacter.getAdjustedMysticality();
				break;

			case 2:
				baseStatValue = KoLCharacter.getBaseMoxie();
				totalStatValue = baseStatValue + KoLCharacter.getAdjustedMoxie();
				break;
		}

		// The wiki claims that your prime stats are somehow connected,
		// but the exact procedure is uncertain.  Therefore, just allow
		// the person to attempt to unlock their store, regardless of
		// their current stats.

		updateDisplay( DISABLE_STATE, "Entering guild challenge area..." );
		KoLRequest request = new KoLRequest( this, "guild.php?place=challenge", true );
		request.run();

		for ( int i = 1; i <= 4; ++i )
		{
			updateDisplay( DISABLE_STATE, "Completing guild task " + i + "..." );
			request = new KoLRequest( this, "guild.php?action=chal", true );
			request.run();
		}

		processResult( new AdventureResult( AdventureResult.ADV, -4 ) );
		updateDisplay( ENABLE_STATE, "Guild store unlocked (maybe)." );
	}

	/**
	 * Confirms whether or not the user wants to make a drunken
	 * request.  This should be called before doing requests when
	 * the user is in an inebrieted state.
	 *
	 * @return	<code>true</code> if the user wishes to adventure drunk
	 */

	protected abstract boolean confirmDrunkenRequest();

	/**
	 * Hosts a massive sale on the items currently in your store.
	 * Utilizes the "minimum meat" principle.
	 */

	public void makeEndOfRunSaleRequest()
	{
		if ( !KoLCharacter.canInteract() )
		{
			updateDisplay( ERROR_STATE, "You are not yet out of ronin." );
			return;
		}

		// Find all tradeable items.  Tradeable items
		// are marked by an autosell value of nonzero.

		AdventureResult [] items = new AdventureResult[ KoLCharacter.getInventory().size() ];
		KoLCharacter.getInventory().toArray( items );

		ArrayList autosell = new ArrayList();
		ArrayList automall = new ArrayList();

		// Only place items in the mall which are not
		// sold in NPC stores -- everything else, make
		// sure you autosell.

		for ( int i = 0; i < items.length; ++i )
		{
			if ( TradeableItemDatabase.getPriceByID( items[i].getItemID() ) != 0 )
			{
				if ( NPCStoreDatabase.contains( items[i].getName() ) )
					autosell.add( items[i] );
				else
					automall.add( items[i] );
			}
		}

		// Now, place all the items in the mall at the
		// maximum possible price.  This allows KoLmafia
		// to determine the minimum price.

		if ( autosell.size() > 0 )
			(new AutoSellRequest( this, autosell.toArray(), AutoSellRequest.AUTOSELL )).run();

		if ( automall.size() > 0 )
			(new AutoSellRequest( this, automall.toArray(), AutoSellRequest.AUTOMALL )).run();

		// Now, remove all the items that you intended
		// to remove from the store due to pricing issues.

		List remove = priceItemsAtLowestPrice();

		for ( int i = 0; i < remove.size(); ++i )
			StoreManager.takeItem( ((StoreManager.SoldItem) remove.get(i)).getItemID() );

		// Now notify the user that everything has been
		// completed to specification.

		if ( remove.isEmpty() )
			updateDisplay( ENABLE_STATE, "Undercutting sale complete." );
		else
			updateDisplay( ENABLE_STATE, "Items available at min-meat removed.  Sale complete." );
	}

	public List priceItemsAtLowestPrice()
	{
		(new StoreManageRequest( this )).run();

		// Now determine the desired prices on items.
		// If the value of an item is currently 100,
		// then remove the item from the store.

		StoreManager.SoldItem [] sold = new StoreManager.SoldItem[ StoreManager.getSoldItemList().size() ];
		StoreManager.getSoldItemList().toArray( sold );

		ArrayList remove = new ArrayList();

		int [] itemID = new int[ sold.length ];
		int [] prices = new int[ sold.length ];
		int [] limits = new int[ sold.length ];

		for ( int i = 0; i < sold.length; ++i )
		{
			itemID[i] = sold[i].getItemID();

			if ( sold[i].getLowest() == 100 || sold[i].getLowest() == TradeableItemDatabase.getPriceByID( itemID[i] ) * 2 )
			{
				prices[i] = sold[i].getPrice();
				remove.add( sold[i] );
			}
			else
			{
				// Because prices that don't end in 00 are
				// seen as undercutting, make sure that the
				// price ends in 00, but is still lower than
				// the current lowest price.

				prices[i] = sold[i].getLowest() - (sold[i].getLowest() % 100);
			}

			limits[i] = 0;
		}

		(new StoreManageRequest( this, itemID, prices, limits )).run();
		updateDisplay( ENABLE_STATE, "Repricing complete." );
		return remove;
	}

	/**
	 * Show an HTML string to the user
	 */

	public abstract void showHTML( String text, String title );

	/**
	 * For requests that do not use the client's "makeRequest()"
	 * method, this method is used to reset the continue state.
	 */

	public void resetContinueState()
	{	this.permitContinue = true;
	}

	/**
	 * Cancels the user's current request.  Note that if there are
	 * no requests running, this method does nothing.
	 */

	public void cancelRequest()
	{	this.permitContinue = false;
	}

	/**
	 * Retrieves whether or not continuation of an adventure or request
	 * is permitted by the client, or by current circumstances in-game.
	 *
	 * @return	<code>true</code> if requests are allowed to continue
	 */

	public boolean permitsContinue()
	{	return permitContinue;
	}

	/**
	 * Initializes a stream for logging debugging information.  This
	 * method creates a <code>KoLmafia.log</code> file in the default
	 * data directory if one does not exist, or appends to the existing
	 * log.  This method should only be invoked if the user wishes to
	 * assist in beta testing because the output is VERY verbose.
	 */

	public static void openDebugLog()
	{
		// First, ensure that a log stream has not already been
		// initialized - this can be checked by observing what
		// class the current log stream is.

		if ( !(logStream instanceof NullStream) )
			return;

		try
		{
			File f = new File( DATA_DIRECTORY + "KoLmafia.log" );

			if ( !f.exists() )
				f.createNewFile();

			logStream = new LogStream( f );
		}
		catch ( IOException e )
		{
			// This should not happen, unless the user
			// security settings are too high to allow
			// programs to write output; therefore,
			// pretend for now that everything works.

			e.printStackTrace( logStream );
			e.printStackTrace();
		}
	}

	public static void closeDebugLog()
	{
		logStream.close();
		logStream = NullStream.INSTANCE;
	}

	/**
	 * Retrieves the current settings for the current session.  Note
	 * that if this is invoked before initialization, this method
	 * will return the global settings.
	 *
	 * @return	The settings for the current session
	 */

	public KoLSettings getSettings()
	{	return settings;
	}

	/**
	 * Retrieves the stream currently used for logging debug output.
	 * @return	The stream used for debug output
	 */

	public static PrintStream getLogStream()
	{	return logStream;
	}

	/**
	 * Initializes the macro recording stream.  This will only
	 * work if no macro streams are currently running.  If
	 * a call is made while a macro stream exists, this method
	 * does nothing.
	 *
	 * @param	filename	The name of the file to be created
	 */

	public void openMacroStream( String filename )
	{
		// First, ensure that a macro stream has not already been
		// initialized - this can be checked by observing what
		// class the current macro stream is.

		if ( !(macroStream instanceof NullStream) )
			return;

		try
		{
			File f = new File( filename );

			if ( !f.exists() )
			{
				f.getParentFile().mkdirs();
				f.createNewFile();
			}

			macroStream = new PrintStream( new FileOutputStream( f, false ) );
		}
		catch ( IOException e )
		{
			// This should not happen, unless the user
			// security settings are too high to allow
			// programs to write output; therefore,
			// pretend for now that everything works.

			e.printStackTrace( logStream );
			e.printStackTrace();
		}
	}

	/**
	 * Retrieves the macro stream.
	 * @return	The macro stream associated with this client
	 */

	public PrintStream getMacroStream()
	{	return macroStream;
	}

	/**
	 * Deinitializes the macro stream.
	 */

	public void closeMacroStream()
	{
		macroStream.close();
		macroStream = NullStream.INSTANCE;
	}

	/**
	 * Returns whether or not the client is currently in a login state.
	 * While the client is in a login state, only login-related
	 * activities should be permitted.
	 */

	public boolean inLoginState()
	{	return isLoggingIn;
	}

	/**
	 * Utility method used to decode a saved password.
	 * This should be called whenever a new password
	 * intends to be stored in the global file.
	 */

	public void addSaveState( String loginname, String password )
	{
		try
		{
			if ( !saveStateNames.contains( loginname ) )
				saveStateNames.add( loginname );

			storeSaveStates();
			String utfString = URLEncoder.encode( password, "UTF-8" );

			StringBuffer encodedString = new StringBuffer();
			char currentCharacter;
			for ( int i = 0; i < utfString.length(); ++i )
			{
				currentCharacter = utfString.charAt(i);
				switch ( currentCharacter )
				{
					case '-':  encodedString.append( "2D" );  break;
					case '.':  encodedString.append( "2E" );  break;
					case '*':  encodedString.append( "2A" );  break;
					case '_':  encodedString.append( "5F" );  break;
					case '+':  encodedString.append( "20" );  break;

					case '%':
						encodedString.append( utfString.charAt( ++i ) );
						encodedString.append( utfString.charAt( ++i ) );
						break;

					default:
						encodedString.append( Integer.toHexString( (int) currentCharacter ).toUpperCase() );
						break;
				}
			}

			GLOBAL_SETTINGS.setProperty( "saveState." + loginname.toLowerCase(), (new BigInteger( encodedString.toString(), 36 )).toString( 10 ) );
		}
		catch ( java.io.UnsupportedEncodingException e )
		{
			// UTF-8 is a very generic encoding scheme; this
			// exception should never be thrown.  But if it
			// is, just ignore it for now.  Better exception
			// handling when it becomes necessary.

			e.printStackTrace( logStream );
			e.printStackTrace();
		}
	}

	public void removeSaveState( String loginname )
	{
		if ( loginname == null )
			return;

		for ( int i = 0; i < saveStateNames.size(); ++i )
			if ( ((String)saveStateNames.get(i)).equalsIgnoreCase( loginname ) )
			{
				saveStateNames.remove( i );
				storeSaveStates();
				return;
			}
	}

	private void storeSaveStates()
	{
		StringBuffer saveStateBuffer = new StringBuffer();
		String [] names = new String[ saveStateNames.size() ];
		saveStateNames.toArray( names );

		if ( names.length > 0 )
		{
			saveStateBuffer.append( names[0] );
			for ( int i = 1; i < names.length; ++i )
			{
				saveStateBuffer.append( "//" );
				saveStateBuffer.append( names[i] );
			}
		}

		GLOBAL_SETTINGS.setProperty( "saveState", saveStateBuffer.toString() );

		// Now, removing any passwords that were stored
		// which are no longer in the save state list


		List lowerCaseNames = new ArrayList();
		for ( int i = 0; i < names.length; ++i )
			lowerCaseNames.add( names[i].toLowerCase() );

		String [] settingsArray = new String[ GLOBAL_SETTINGS.keySet().size() ];
		GLOBAL_SETTINGS.keySet().toArray( settingsArray );

		for ( int i = 0; i < settingsArray.length; ++i )
			if ( settingsArray[i].startsWith( "saveState." ) && !lowerCaseNames.contains( settingsArray[i].substring( 10 ) ) )
				GLOBAL_SETTINGS.remove( settingsArray[i] );
	}

	/**
	 * Utility method used to decode a saved password.
	 * This should be called whenever a new password
	 * intends to be stored in the global file.
	 */

	public String getSaveState( String loginname )
	{
		try
		{
			Object [] settingKeys = GLOBAL_SETTINGS.keySet().toArray();
			String password = null;
			String lowerCaseKey = "saveState." + loginname.toLowerCase();
			String currentKey;

			for ( int i = 0; i < settingKeys.length && password == null; ++i )
			{
				currentKey = (String) settingKeys[i];
				if ( currentKey.equals( lowerCaseKey ) )
					password = GLOBAL_SETTINGS.getProperty( currentKey );
			}

			if ( password == null )
				return null;

			String hexString = (new BigInteger( password, 10 )).toString( 36 );
			StringBuffer utfString = new StringBuffer();
			for ( int i = 0; i < hexString.length(); ++i )
			{
				utfString.append( '%' );
				utfString.append( hexString.charAt(i) );
				utfString.append( hexString.charAt(++i) );
			}

			return URLDecoder.decode( utfString.toString(), "UTF-8" );
		}
		catch ( java.io.UnsupportedEncodingException e )
		{
			// UTF-8 is a very generic encoding scheme; this
			// exception should never be thrown.  But if it
			// is, just ignore it for now.  Better exception
			// handling when it becomes necessary.

			e.printStackTrace( logStream );
			e.printStackTrace();

			return null;
		}
	}

	public SortedListModel getSessionTally()
	{	return tally;
	}

	public SortedListModel getConditions()
	{	return conditions;
	}

	public LockableListModel getAdventureList()
	{	return adventureList;
	}

	public LockableListModel getEncounterList()
	{	return encounterList;
	}

	public void executeTimeInRequest()
	{
		// If you're already trying to login, then
		// don't continue.

		if ( isLoggingIn )
			return;

		// If the client is permitted to continue,
		// then the session has already timed in.

		if ( permitsContinue() )
		{
			updateDisplay( ERROR_STATE, "No timeout detected." );
			return;
		}

		isLoggingIn = true;
		LoginRequest cachedLogin = this.cachedLogin;

		deinitialize();
		updateDisplay( DISABLE_STATE, "Timing in session..." );

		// Two quick login attempts to force
		// a timeout of the other session and
		// re-request another session.

		cachedLogin.run();

		if ( isLoggingIn )
		{
			resetContinueState();
			cachedLogin.run();
		}

		// Wait 5 minutes inbetween each attempt
		// to re-login to Kingdom of Loathing,
		// because if the above two failed, that
		// means it's nightly maintenance.

		int retryCount = 0;

		while ( isLoggingIn && ++retryCount < 10 )
		{
			for ( int i = 300; i > 0; --i )
			{
				updateDisplay( DISABLE_STATE, i + " second" + (i == 1 ? "" : "s") + " before next retry attempt..." );
				KoLRequest.delay( 1000 );
			}

			resetContinueState();
			cachedLogin.run();
		}

		// If it took more than four retries, then
		// go ahead and stop.  If the time-in was
		// automated retry, then it will repeat
		// these four retries four more times
		// before completely stopping.

		if ( retryCount == 10 )
		{
			updateDisplay( ERROR_STATE, "Session time-in failed." );
			cancelRequest();
			return;
		}

		// Refresh the character data after a
		// successful login.

		(new CharsheetRequest( KoLmafia.this )).run();

		resetContinueState();
		updateDisplay( ENABLE_STATE, "Session timed in." );
	}

	public boolean checkRequirements( List requirements )
	{
		AdventureResult [] requirementsArray = new AdventureResult[ requirements.size() ];
		requirements.toArray( requirementsArray );

		int missingCount;
		missingItems.clear();

		// Check the items required for this quest,
		// retrieving any items which might be inside
		// of a closet somewhere.

		for ( int i = 0; i < requirementsArray.length; ++i )
		{
			if ( requirementsArray[i] == null )
				continue;

			missingCount = 0;

			if ( requirementsArray[i].isItem() )
			{
				AdventureDatabase.retrieveItem( requirementsArray[i] );
				missingCount = requirementsArray[i].getCount() - requirementsArray[i].getCount( KoLCharacter.getInventory() );
			}
			else if ( requirementsArray[i].isStatusEffect() )
			{
				// Status effects should be compared against
				// the status effects list.  This is used to
				// help people detect which effects they are
				// missing (like in PVP).

				missingCount = requirementsArray[i].getCount() - requirementsArray[i].getCount( KoLCharacter.getEffects() );
			}
			else if ( requirementsArray[i].getName().equals( AdventureResult.MEAT ) )
			{
				// Currency is compared against the amount
				// actually liquid -- amount in closet is
				// ignored in this case.

				missingCount = requirementsArray[i].getCount() - KoLCharacter.getAvailableMeat();
			}

			if ( missingCount > 0 )
			{
				// If there are any missing items, add
				// them to the list of needed items.

				missingItems.add( requirementsArray[i].getInstance( missingCount ) );

				// Allow later requirements to be created.
				// We'll cancel the request again later.

				resetContinueState();
			}
		}

		// If there are any missing requirements
		// be sure to return false.

		if ( !missingItems.isEmpty() )
		{
			updateDisplay( ERROR_STATE, "Insufficient items to continue." );
			printList( missingItems );
			cancelRequest();
			return false;
		}

		updateDisplay( NORMAL_STATE, "Requirements met." );
		return true;
	}

	/**
	 * Utility method used to print a list to the given output
	 * stream.  If there's a need to print to the current output
	 * stream, simply pass the output stream to this method.
	 */

	protected abstract void printList( List printing );

	/**
	 * Utility method used to purchase the given number of items
	 * from the mall using the given purchase requests.
	 */

	public void makePurchases( List results, Object [] purchases, int maxPurchases )
	{
		if ( purchases.length > 0 && purchases[0] instanceof MallPurchaseRequest )
			macroStream.print( "buy " + maxPurchases + " " + ((MallPurchaseRequest)purchases[0]).getItemName() );

		MallPurchaseRequest currentRequest;
		resetContinueState();

		int purchaseCount = 0;

		for ( int i = 0; i < purchases.length && purchaseCount != maxPurchases && permitsContinue(); ++i )
		{
			if ( purchases[i] instanceof MallPurchaseRequest )
			{
				currentRequest = (MallPurchaseRequest) purchases[i];

				if ( !KoLCharacter.canInteract() && currentRequest.getQuantity() != MallPurchaseRequest.MAX_QUANTITY )
				{
					updateDisplay( ERROR_STATE, "You are not yet out of ronin." );
					return;
				}

				AdventureResult result = new AdventureResult( currentRequest.getItemName(), 0, false );

				// Keep track of how many of the item you had before
				// you run the purchase request

				int oldResultCount = result.getCount( KoLCharacter.getInventory() );
				int previousLimit = currentRequest.getLimit();

				currentRequest.setLimit( Math.min( previousLimit, maxPurchases - purchaseCount ) );
				currentRequest.run();

				// Calculate how many of the item you have now after
				// you run the purchase request

				int newResultCount = result.getCount( KoLCharacter.getInventory() );
				purchaseCount += newResultCount - oldResultCount;

				// Remove the purchase from the list!  Because you
				// have already made a purchase from the store

				if ( permitsContinue() )
				{
					if ( currentRequest.getQuantity() == currentRequest.getLimit() )
						results.remove( currentRequest );
					else if ( currentRequest.getQuantity() == MallPurchaseRequest.MAX_QUANTITY )
						currentRequest.setLimit( MallPurchaseRequest.MAX_QUANTITY );
					else
					{
						if ( currentRequest.getLimit() == previousLimit )
							currentRequest.setCanPurchase( false );

						currentRequest.setQuantity( currentRequest.getQuantity() - currentRequest.getLimit() );
						currentRequest.setLimit( previousLimit );
					}
				}
			}
		}

		// With all that information parsed out, we should
		// refresh the lists at the very end.

		KoLCharacter.refreshCalculatedLists();
		if ( purchaseCount == maxPurchases || maxPurchases == Integer.MAX_VALUE )
			updateDisplay( NORMAL_STATE, "Purchases complete." );
		else
			updateDisplay( ERROR_STATE, "Desired purchase quantity not reached." );
	}

	/**
	 * Utility method used to register a given adventure in
	 * the running adventure summary.
	 */

	public void registerAdventure( KoLAdventure adventureLocation )
	{
		String adventureName = adventureLocation.getAdventureName();
		RegisteredEncounter lastAdventure = (RegisteredEncounter) adventureList.lastElement();

		if ( lastAdventure != null && lastAdventure.name.equals( adventureName ) )
		{
			++lastAdventure.encounterCount;

			// Manually set to force repainting in GUI
			adventureList.set( adventureList.size() - 1, lastAdventure );
		}
		else
			adventureList.add( new RegisteredEncounter( adventureName ) );
	}

	/**
	 * Utility method used to register a given encounter in
	 * the running adventure summary.
	 */

	public void registerEncounter( String encounterName )
	{
		encounterName = encounterName.toLowerCase().trim();

		RegisteredEncounter [] encounters = new RegisteredEncounter[ encounterList.size() ];
		encounterList.toArray( encounters );

		for ( int i = 0; i < encounters.length; ++i )
		{
			if ( encounters[i].name.equals( encounterName ) )
			{
				++encounters[i].encounterCount;

				// Manually set to force repainting in GUI
				encounterList.set( i, encounters[i] );
				return;
			}
		}

		encounterList.add( new RegisteredEncounter( encounterName ) );
	}

	private class RegisteredEncounter
	{
		private String name;
		private int encounterCount;

		public RegisteredEncounter( String name )
		{
			this.name = name;
			encounterCount = 1;
		}

		public String toString()
		{	return name + " (" + encounterCount + ")";
		}
	}

	public KoLRequest getCurrentRequest()
	{	return currentRequest;
	}

	public void setCurrentRequest( KoLRequest request)
	{	currentRequest = request;
	}

	public void setLocalProperty( String property, String value )
	{	LOCAL_SETTINGS.setProperty( property, value );
	}

	public void setLocalProperty( String property, boolean value )
	{	LOCAL_SETTINGS.setProperty( property, String.valueOf( value ) );
	}

	public void setLocalProperty( String property, int value )
	{	LOCAL_SETTINGS.setProperty( property, String.valueOf( value ) );
	}

	public String getLocalProperty( String property )
	{
		String value = LOCAL_SETTINGS.getProperty( property );
		return ( value == null) ? "" : value;
	}

	public boolean getLocalBooleanProperty( String property )
	{
		String value = LOCAL_SETTINGS.getProperty( property );
		return ( value == null) ? false : value.equals( "true" );
	}

	public int getLocalIntegerProperty( String property )
	{
		String value = LOCAL_SETTINGS.getProperty( property );
		return ( value == null) ? 0 : Integer.parseInt( value );
	}

	public final String [] extractTargets( String targetList )
	{
		// If there are no targets in the list, then
		// return absolutely nothing.

		if ( targetList == null || targetList.trim().length() == 0 )
			return new String[0];

		// Otherwise, split the list of targets, and
		// determine who all the unique targets are.

		String [] targets = targetList.trim().split( "\\s*,\\s*" );
		for ( int i = 0; i < targets.length; ++i )
			targets[i] = getPlayerID( targets[i] ) == null ? targets[i] :
				getPlayerID( targets[i] );

		// Sort the list in order to increase the
		// speed of duplicate detection.

		Arrays.sort( targets );

		// Determine who all the duplicates are.

		int uniqueListSize = targets.length;
		for ( int i = 1; i < targets.length; ++i )
		{
			if ( targets[i].equals( targets[ i - 1 ] ) )
			{
				targets[ i - 1 ] = null;
				--uniqueListSize;
			}
		}

		// Now, create the list of unique targets;
		// if the list has the same size as the original,
		// you can skip this step.

		if ( uniqueListSize != targets.length )
		{
			int addedCount = 0;
			String [] uniqueList = new String[ uniqueListSize ];
			for ( int i = 0; i < targets.length; ++i )
				if ( targets[i] != null )
					uniqueList[ addedCount++ ] = targets[i];

			targets = uniqueList;
		}

		// Convert all the user IDs back to the
		// original player names so that the results
		// are easy to understand for the user.

		for ( int i = 0; i < targets.length; ++i )
			targets[i] = getPlayerName( targets[i] ) == null ? targets[i] :
				getPlayerName( targets[i] );

		// Sort the list one more time, this time
		// by player name.

		Arrays.sort( targets );

		// Parsing complete.  Return the list of
		// unique targets.

		return targets;
	}

	public final void downloadAdventureOverride()
	{
		updateDisplay( DISABLE_STATE, "Downloading adventure data file patch..." );

		try
		{
			BufferedReader reader = new BufferedReader( new InputStreamReader(
				(InputStream) (new URL( "http://kolmafia.sourceforge.net/data/adventures.dat" )).getContent() ) );

			File output = new File( "data/adventures.dat" );
			if ( output.exists() )
				output.delete();

			String line;
			PrintStream writer = new PrintStream( new FileOutputStream( output ) );

			while ( (line = reader.readLine()) != null )
				writer.println( line );

			writer.close();
		}
		catch ( IOException e )
		{
			updateDisplay( ERROR_STATE, "Error occurred in download attempt." );

			e.printStackTrace( logStream );
			e.printStackTrace();
			return;
		}

		AdventureDatabase.refreshTable();
		AdventureDatabase.getAsLockableListModel();
		updateDisplay( ENABLE_STATE, "Adventure table updated." );
	}
}
