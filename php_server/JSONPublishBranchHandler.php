<?php

include_once 'Contact.php';
include_once 'JSONProtocol.php';


class JSONInitPublishBranchHandler extends JSONHandler {
	public function getAuthToken() {
		return "rand".rand()."time".time();
	}
	
	public function hasBranch($branch) {
		$profile = Session::get()->getProfile($this->receiver);
		if ($profile === null)
			return false;
		$mailbox = $profile->getMainMailbox();
		if ($mailbox === null)
			return false;
		
		$part1 = substr($branch, 0, 2);
		$part2 = substr($branch, 2);
		$part2Dirs = $mailbox->listDirectories($part1);
		return in_array($part2, $part1Dirs);
	}

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "initPublishBranch") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['branch']))
			return false;
		$branch = $params['branch'];
		if ($branch == "")
			return false;

		$authToken = $this->getAuthToken()
		$messageChannelNeeded = $this->hasBranch($branch);

		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' = > "sign this token",
			'authToken' => $authToken, 'messageChannelNeeded' => $messageChannelNeeded));
	}
}

class JSONLoginPublishBranchHandler extends JSONHandler {
	public function loginPublishBranch($branch, $signedToken) {
		if ($branch === null)
			return "";

		
		
	}

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "initPublishBranch") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['signedToken']) || !isset($params['branch']))
			return makeError($jsonId, "loginPublishBranch: bad arguments");

		$remoteTip = loginPublishBranch($params['branch']), $params['signedToken'])
		if ($remoteTip == null)
			return makeError($jsonId, "loginPublishBranch: no access to branch");

		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' = > "ready to sync",
			'remoteTip' => $remoteTip));
	}

	public function makeError($jsonId, $message) {
		return $this->makeJSONRPCReturn($jsonId, array('status' => false, 'message' => $message));
	}
}


?> 