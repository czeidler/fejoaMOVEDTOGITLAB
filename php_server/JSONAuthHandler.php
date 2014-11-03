<?php

include_once 'Session.php';
include_once 'JSONProtocol.php';


class AuthConst {
	static public $kAuthStanza = "auth";
	static public $kAuthSignedStanza = "auth_signed";
	static public $kLogoutStanza = "logout"; 
}


class AuthTransaction extends Transaction {
	public $signToken = "";

	public function __construct($signToken) {
		parent::__construct();
		$this->signToken = $signToken;
	}
}

class JSONAuthHandler extends JSONHandler {
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], AuthConst::$kAuthStanza) != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['type']) || !isset($params['loginUser']) || !isset($params['serverUser']))
			return false;

		$authType = $params['type'];
		$loginUser = $params['loginUser'];
		$serverUser = $params['serverUser'];
		if (strcmp($authType, "signature") != 0)
			return $this->makeError($jsonId, "Unsupported authentication type.");

		$signToken = "rand".rand()."time".time();
		$transaction = new AuthTransaction($signToken);
		Session::get()->addTransaction($transaction);

		// Check if the user has a chance to login, i.e., if we know him
		$userIdentity = Session::get()->getMainUserIdentity($serverUser);
		// if $userIdentity is null the database is invalid give the user a change to upload a profile
		if ($userIdentity != null) {
			$contact = $userIdentity->findContact($loginUser);
			if ($userIdentity->getMyself()->getUid() != $loginUser && $contact === null) {
				// produce output
				return $this->makeError($jsonId, "I don't know you");
			}
		}

		// produce output
		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, 'transactionId' => $transaction->getUid(),
			'signToken' => $signToken));
	}
}


class JSONAuthSignedHandler extends JSONHandler {
	private $errorMessage = "";
	private $serverUser = "";
	private $signature = "";

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], AuthConst::$kAuthSignedStanza) != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;
		if (!isset($params['transactionId']) || !isset($params['signature']) || !isset($params['loginUser']) || !isset($params['serverUser']))
			return false;

		$transaction = Session::get()->getTransaction($params['transactionId']);
		if ($transaction === null)
			return $this->makeError($jsonId, "bad transaction id: ".$params['transactionId']);
		// cleanup transaction
		Session::get()->removeTransaction($transaction);

		$this->signature = url_decode($params['signature']);
		$this->serverUser = $params['serverUser'];
		$loginUser = $params['loginUser'];
		
		$userIdentity = Session::get()->getMainUserIdentity($this->serverUser);

		$status = false;
		$roles = Session::get()->getUserRoles();
		if ($userIdentity != null) {
			$myself = $userIdentity->getMyself();
			if ($myself->getUid() == $loginUser) {
				$status = $this->accountLogin($myself, $transaction, $roles);
			} else {
				$contact = $userIdentity->findContact($loginUser);
				if ($contact !== null)
					$status = $this->userLogin($contact, $transaction, $roles);
				else
					$this->errorMessage = "can't find user: ".$loginUser;
			}
		} else {
			$status = $this->setupLogin($transaction, $roles);
		}
		// cleanup from double logins.
		//TODO: check earlier if verification is necessary? so that we don't need to cleanup here
		$roles = $this->removeDuplicates($roles);
		Session::get()->setUserRoles($roles);

		// produce output
		if (!$status)
			return $this->makeError($jsonId, "denied: ".$this->errorMessage);

		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, "roles" => $roles));
	}

	private function removeDuplicates($array){
		$cleanedArray = array();
		foreach($array as $key=>$value)
			$cleanedArray[$value] = 1;
		return array_keys($cleanedArray);
	}

	private function setupLogin($transaction, &$roles) {
		$signatureFileName = $this->serverUser."/signature.pup";
		$publickey = "";
		if (file_exists($signatureFileName))
			$publickey = file_get_contents($signatureFileName);

		$signatureVerifier = new SignatureVerifier($publickey);
		if (!$signatureVerifier->verify($transaction->signToken, $this->signature)) {
			$this->errorMessage = "can't verify setup login";
			return false;
		}
		Session::get()->setAccountUser($this->serverUser);
		$roles[] =  "account";
		return true;
	}

	private function accountLogin($contact, $transaction, &$roles) {
		if (!$contact->verify($contact->getMainKeyId(), $transaction->signToken, $this->signature)) {
			$this->errorMessage = "can't verify account login";
			return false;
		}
		Session::get()->setAccountUser($this->serverUser);
		$roles[] =  "account";
		return true;
	}

	private function userLogin($contact, $transaction, &$roles) {
		if (!$contact->verify($contact->getMainKeyId(), $transaction->signToken, $this->signature)) {
			$this->errorMessage = "can't verify user login";
			return false;
		}
		$loginServerUser = $this->serverUser;
		$roles[] = $loginServerUser.":contact_user";
		
		return true;
	}
}

class JSONLogoutHandler extends JSONHandler {
	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], AuthConst::$kLogoutStanza) != 0)
			return false;

		Session::get()->clear();

		return $this->makeJSONRPCReturn($jsonId, array('status' => 0, "message" => "logged out"));
	}
}
?>
