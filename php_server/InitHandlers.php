<?php

include_once 'ContactRequestHandler.php';
include_once 'MessageHandler.php';
include_once 'SyncHandler.php';
include_once 'WatchBranchHandler.php';

include_once 'JSONAuthHandler.php';
include_once 'JSONContactRequestHandler.php';
include_once 'JSONPublishBranchHandler.php';


function initSyncHandlers($XMLHandler) {
	// pull
	$pullIqGetHandler = new InIqStanzaHandler(IqType::$kGet);
	$pullHandler = new SyncPullStanzaHandler($XMLHandler->getInStream());
	$pullIqGetHandler->addChild($pullHandler);
	$XMLHandler->addHandler($pullIqGetHandler);

	// push
	$pushIqGetHandler = new InIqStanzaHandler(IqType::$kSet);
	$pushHandler = new SyncPushStanzaHandler($XMLHandler->getInStream());
	$pushIqGetHandler->addChild($pushHandler);
	$XMLHandler->addHandler($pushIqGetHandler);
}

function initWatchBranchesStanzaHandler($XMLHandler) {
	$iqHandler = new InIqStanzaHandler(IqType::$kGet);
	$handler = new WatchBranchesStanzaHandler($XMLHandler->getInStream());
	$iqHandler->addChild($handler);
	$XMLHandler->addHandler($iqHandler);
}


function initContactRequestStanzaHandler($XMLHandler) {
	$iqHandler = new InIqStanzaHandler(IqType::$kGet);
	$handler = new ContactRequestStanzaHandler($XMLHandler->getInStream());
	$iqHandler->addChild($handler);
	$XMLHandler->addHandler($iqHandler);
}

function initContactRequestStanzaHandlerJson($JSONDispatcher) {
	$JSONDispatcher->addHandler(new JSONContactRequestHandler());
}

// auth
function initAuthHandlers($JSONDispatcher) {
	$JSONDispatcher->addHandler(new JSONAuthHandler());
	$JSONDispatcher->addHandler(new JSONAuthSignedHandler());
	$JSONDispatcher->addHandler(new JSONLogoutHandler());
}

// auth
function initPublishBranchHandlers($JSONDispatcher) {
	$JSONDispatcher->addHandler(new JSONInitPublishBranchHandler());
	$JSONDispatcher->addHandler(new JSONLoginPublishBranchHandler());
}

function initMessageHandlers($XMLHandler) {
	$iqHandler = new InIqStanzaHandler(IqType::$kSet);
	$handler = new MessageStanzaHandler($XMLHandler->getInStream());
	$iqHandler->addChild($handler);
	$XMLHandler->addHandler($iqHandler);	
}


class InitHandlers {
	static public function initPrivateHandlers($XMLHandler, $JSONDispatcher) {
		// xml
		initSyncHandlers($XMLHandler);
		initMessageHandlers($XMLHandler);
		initWatchBranchesStanzaHandler($XMLHandler);
		initContactRequestStanzaHandler($XMLHandler);

		// json
		initAuthHandlers($JSONDispatcher);
		initPublishBranchHandlers($JSONDispatcher);
	}

	static public function initPublicHandlers($XMLHandler, $JSONDispatcher) {
		// xml
		initSyncHandlers($XMLHandler);
		initMessageHandlers($XMLHandler);
		initContactRequestStanzaHandler($XMLHandler);

		// json
		initAuthHandlers($JSONDispatcher);
		initPublishBranchHandlers($JSONDispatcher);
		initContactRequestStanzaHandlerJson($JSONDispatcher);
	}
}

?>
