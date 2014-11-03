<?php

include_once 'Contact.php';
include_once 'JSONProtocol.php';


class PublishBranchTransaction extends Transaction {
	public $serverUser = "";
	public $signToken = "";

	public function __construct($signToken) {
		parent::__construct();
		$this->signToken = $signToken;
	}
}

class PublishBranchHelper {
	static public function getMessageChannel($serverUser, $messageChannel) {
		$profile = Session::get()->getProfile($serverUser);
		if ($profile === null)
			return null;
		$mailbox = $profile->getMainMailbox();
		if ($mailbox === null)
			return null;
		return new MessageChannel($mailbox, $messageChannel);
	}

	static public function hasBranch($serverUser, $branch) {
		$profile = Session::get()->getProfile($serverUser);
		if ($profile === null)
			return false;
		$mailbox = $profile->getMainMailbox();
		if ($mailbox === null)
			return false;
		
		$part1 = substr($branch, 0, 2);
		$part2 = substr($branch, 2);
		$part2Dirs = $mailbox->listDirectories($part1);
		return in_array($part2, $part2Dirs);
	}
}

class JSONInitPublishBranchHandler extends JSONHandler {
	public function getAuthToken() {
		return "rand".rand()."time".time();
	}

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "initPublishBranch") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['serverUser']) || !isset($params['branch']))
			return false;
		$branch = $params['branch'];
		if ($branch == "")
			return false;

		$serverUser = $params['serverUser'];
		$signToken = $this->getAuthToken();
		$messageChannelNeeded = !PublishBranchHelper::hasBranch($serverUser, $branch);

		$transaction = new PublishBranchTransaction($signToken);
		$transaction->serverUser = $serverUser;
		Session::get()->addTransaction($transaction);
		
		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' => "sign this token",
			'transactionId' => $transaction->getUid(), 'signToken' => $signToken,
			'messageChannelNeeded' => $messageChannelNeeded));
	}
}

class JSONLoginPublishBranchHandler extends JSONHandler {

	private function pemToDer($Pem) {
		// from: http://pumka.net/2009/12/19/reading-writing-and-converting-rsa-keys-in-pem-der-publickeyblob-and-privatekeyblob-formats/
		//Split lines:
		$lines = explode("\n", trim($Pem));
		//Remove last and first line:
		unset($lines[count($lines)-1]);
		unset($lines[0]);
		//Join remaining lines:
		$result = implode('', $lines);
		//Decode:
		$result = base64_decode($result);
	
		return $result;
	}

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "loginPublishBranch") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['transactionId']) ||!isset($params['signedToken']) || !isset($params['branch']))
			return $this->makeError($jsonId, "loginPublishBranch: bad arguments");

		$transaction = Session::get()->getTransaction($params['transactionId']);
		if ($transaction === null)
			return $this->makeError($jsonId, "bad transaction id: ".$params['transactionId']);
		// cleanup transaction
		Session::get()->removeTransaction($transaction);

		$branch = $params['branch'];
		$branchAccessToken = $transaction->serverUser.":".$branch;
		$messageChannel = PublishBranchHelper::getMessageChannel($transaction->serverUser, $branch);

		$signedToken = url_decode($params['signedToken']);
		// verify branch access
		if (!PublishBranchHelper::hasBranch($transaction->serverUser, $branch)) {
			if (!isset($params['channelHeader']) || !isset($params['channelSignatureKey']))
				return $this->makeError($jsonId, "message channel is needed");

			// verify
			$signatureKey = $params['channelSignatureKey'];
			if (strcasecmp(hash('sha256', $this->pemToDer($signatureKey)), $branch) != 0)
				return $this->makeError($jsonId, "mismatch between branch and signature key");
			$signatureVerifier = new SignatureVerifier($signatureKey);
			if (!$signatureVerifier->verify($transaction->signToken, $signedToken))
				return $this->makeError($jsonId, "failed to verify branch access");

			$messageChannel->setChannelInfo(url_decode($params['channelHeader']));
			$messageChannel->setSignatureKey($params['channelSignatureKey']);

			Session::get()->addBranchAccess($branchAccessToken);
		} else if (!Session::get()->hasBranchAccess($branchAccessToken)) {
			$signatureKey = $messageChannel->getSignatureKey();
			if ($signatureKey === null)
				return $this->makeError($jsonId, "bad message channel on server");

			// verify
			if (strcasecmp(hash('sha256', $this->pemToDer($signatureKey)), $branch) != 0)
				return $this->makeError($jsonId, "mismatch between branch and signature key");
			$signatureVerifier = new SignatureVerifier($signatureKey);
			if (!$signatureVerifier->verify($transaction->signToken, $signedToken))
				return $this->makeError($jsonId, "failed to verify branch access");
			Session::get()->addBranchAccess($branchAccessToken);
		}

		$databaseDir = $transaction->serverUser."/".$messageChannel->getDatabaseDir();
		$database = new GitDatabase($databaseDir);
		$tip = $database->getBranchTip($branch);

		// reply
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'message' => "ready to sync",
			'remoteTip' => $tip));
	}
}


?> 