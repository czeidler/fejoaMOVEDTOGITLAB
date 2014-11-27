<?php

include_once 'JSONProtocol.php';


class JSONSyncPullHandler extends JSONHandler {
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "syncPull") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['branch']) || !isset($params['serverUser']) || !isset($params['base']))
			return false;
		$serverUser = $params['serverUser'];
		$branch = $params['branch'];
		$remoteTip = $params['base'];

		// pull
		$branchAccessToken = $serverUser.":".$branch;
		if (!Session::get()->isAccountUser() && !Session::get()->hasBranchAccess($branchAccessToken))
			return $this->makeError($jsonId, "pull: no access");
		$database = Session::get()->getDatabase($serverUser);
		if ($database === null)
			return false;

		if (isSHA1Hex($remoteTip))
			$remoteTip = sha1_bin($remoteTip);

		$packManager = new PackManager($database);
		$pack = "";
		try {
			$localTip = $database->getTip($branch);
			$pack = $packManager->exportPack($branch, $remoteTip, $localTip, -1);
		} catch (Exception $e) {
			$localTip = "";
		}

		$localTipHex = "";
		$remoteTipHex = "";
		if (strlen($remoteTip) == 20)
			$remoteTipHex = sha1_hex($remoteTip);
		if (strlen($localTip) == 20)
			$localTipHex = sha1_hex($localTip);

		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' => "",
			'branch' => $branch, 'base' => $remoteTipHex, 'tip' => $localTipHex, 'pack' => base64_encode($pack)));
	}
}


class JSONSyncPushHandler extends JSONHandler {
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "syncPush") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['branch']) || !isset($params['serverUser']) || !isset($params['startCommit'])
			|| !isset($params['lastCommit']) || !isset($params['pack']))
			return false;
		$serverUser = $params['serverUser'];
		$branch = $params['branch'];
		$startCommit = $params['startCommit'];
		$lastCommit = $params['lastCommit'];
		$pack = url_decode($params['pack']);
 
		// push
		$branchAccessToken = $serverUser.":".$branch;
		if (!Session::get()->isAccountUser() && !Session::get()->hasBranchAccess($branchAccessToken))
			return $this->makeError($jsonId, "Push: access to branch denied.");
		$database = Session::get()->getDatabase($serverUser);
		if ($database === null)
			return $this->makeError($jsonId, "Push: no such branch.");

		$packManager = new PackManager($database);
		if (!$packManager->importPack($branch, $pack, $startCommit, $lastCommit))
			return $this->makeError($jsonId, "Push: unable to import pack.");

		$localTip = sha1_hex($database->getTip($branch));

		// if somebody else sent us the branch update the tip in the mailbox so that the client
		// finds out about the new update
		if (Session::get()->getAccountUser() != $serverUser) {
			$mailbox = Session::get()->getMainMailbox($serverUser);
			if ($mailbox != null) {
				if ($mailbox->updateChannelTip($branch, $localTip))
					$mailbox->commit();
			}
		}

		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' => "",
			'branch' => $branch, 'tip' => $localTip));
	}
}

?>