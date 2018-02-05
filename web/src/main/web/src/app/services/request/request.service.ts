import {Injectable, OnDestroy, OnInit} from '@angular/core';
import {Store} from '@ngrx/store';
import {HttpClient} from '@angular/common/http';

import {RequestsStats} from '../../core/requests/RequestsStats';
import {IState} from '../../redux/store';
import {Utils} from '../../shared/utils';
import {
  ClaimRequestCommand,
  createRequest,
  FundRequestCommand,
  IRequest,
  IRequestList,
  IRequestRecord,
  RequestIssueFundInformation,
  RequestStatus,
  SignedClaim
} from '../../redux/requests.models';
import {IUserRecord} from '../../redux/user.models';
import {IAccountWeb3Record} from '../../redux/accountWeb3.models';
import {AddRequest, EditRequest, RemoveRequest, ReplaceRequestList} from '../../redux/requests.reducer';
import {ContractsService} from '../contracts/contracts.service';
import {AccountWeb3Service} from '../accountWeb3/account-web3.service';

import {Observable} from 'rxjs/Observable';
import {Subscription} from 'rxjs/Subscription';
import 'rxjs/add/operator/toPromise';
import 'rxjs/add/operator/take';
import {ApiUrls} from '../../api/api.urls';

@Injectable()
export class RequestService implements OnInit, OnDestroy {
  private _requestInitialized: boolean = false;
  private _accountWeb3: IAccountWeb3Record;
  private _subscription: Subscription;
  private _web3: any;

  constructor(private store: Store<IState>,
    private _http: HttpClient,
    private _aw3s: AccountWeb3Service,
    private _cs: ContractsService) {
  }

  public async ngOnInit() {
    this._subscription = this._aw3s.currentAccountWeb3$.subscribe((accountWeb3: IAccountWeb3Record) => {
      this._accountWeb3 = accountWeb3;
      this._web3 = this._aw3s.getWeb3(this._accountWeb3);
    });
  }

  public async canClaim(request: IRequestRecord): Promise<boolean> {
    console.log('start can claim');
    if (typeof request == 'undefined' || request.status == RequestStatus.CLAIMED) {
      return Promise.resolve(false);
    } else {
      console.log('can claim', request);
      let canClaim: boolean = await this._http.get(ApiUrls.canClaim(request.id)).toPromise() as boolean;
      return canClaim;
    }
  }

  public get requests$(): Observable<IRequestList> {
    if (!this._requestInitialized) {
      this._requestInitialized = true;

      this._http.get(ApiUrls.requests).take(1).subscribe((requests: Array<IRequest>) => {
        let requestsList: Array<IRequestRecord> = [];
        requests.forEach((request) => {
          requestsList.push(createRequest(request));
        });
        this.store.dispatch(new ReplaceRequestList(requestsList));
        this.store.select(state => state.requests).take(1).subscribe((requests: IRequestList) => {
          requests.map((request: IRequestRecord) => {
            this.updateRequestWithNewFundInfoFromContract(request);
          });
        });
      });
    }

    return this.store.select(state => state.requests);
  }

  public async getStatistics(): Promise<RequestsStats> {
    return this._cs.getStatistics();
  }

  public addRequest(issueLink: string, fundAmount: number): void {
    this._cs.fundRequest('GITHUB', Utils.getPlatformIdFromUrl(issueLink), fundAmount);
  }

  public async fundRequest(command: FundRequestCommand): Promise<string> {
    return this._cs.fundRequest(command.platform, command.platformId, command.amount);
  }

  public async requestQRValue(command: FundRequestCommand) {
    let body = {
      platform: command.platform,
      platformId: command.platformId,
      amount: '' + this._web3.toWei(command.amount, 'ether'),
      fundrequestAddress: this._cs.getFundRequestContractAddress(),
      tokenAddress: this._cs.getTokenContractAddress()
    };
    return await this._http.post(ApiUrls.qrFund, body, {responseType: 'text'}).toPromise();
  }

  public async claimRequest(command: ClaimRequestCommand): Promise<string> {
    let body = {
      platform: command.platform,
      platformId: command.platformId,
      address: this._accountWeb3.currentAccount
    };
    await this._http.post(ApiUrls.claim(command.id), body).take(1).subscribe((signedClaim: SignedClaim) => {
      return this._cs.claimRequest(signedClaim);
    });
    return null;
  }

  public async hasPullRequestMerged(request: IRequestRecord): Promise<boolean> {
    console.log('has pull request', request, Utils.getUrlFromRequest(request));
    this._http.get(Utils.getUrlFromRequest(request), {responseType: 'text'}).take(1).subscribe((res) => {
      console.log(res);
    });

    return true;
  }

  public setUserAsWatcher(request: IRequestRecord, user: IUserRecord): void {
    this.updateWatcher(request, user.email, true);
  }

  public unSetUserAsWatcher(request: IRequestRecord, user: IUserRecord): void {
    this.updateWatcher(request, user.email, false);
  }

  private updateWatcher(request: IRequestRecord, userId: string, add: boolean): void {
    let newWatchers: string[] = JSON.parse(JSON.stringify(request.watchers));

    add ? newWatchers.push(userId) : newWatchers.filter(function (watcher) {
      return watcher != userId;
    });

    let newRequest: IRequestRecord = createRequest(JSON.parse(JSON.stringify(request)));
    newRequest = newRequest.set('watchers', newWatchers);
    this.editRequestInStore(request, newRequest);

    let httpUrl = ApiUrls.watchers(request.id);
    let httpCall: Observable<Object>;

    add ? httpCall = this._http.put(httpUrl, {
      responseType: 'text',
      requestId: request.id
    }) : httpCall = this._http.delete(httpUrl);

    httpCall.take(1).subscribe(null,
      error => {
        this.editRequestInStore(newRequest, request); // if something when wrong, update it back to the old value
        this.handleError(error);
      }
    );
  }

  public removeRequestInStore(request: IRequestRecord): void {
    this.store.dispatch(new RemoveRequest(request));
  }

  public addRequestInStore(newRequest: IRequestRecord): void {
    this.store.dispatch(new AddRequest(newRequest));
    this.updateRequestBalance(newRequest);
  }

  public editRequestInStore(oldRequest: IRequestRecord, modifiedRequest: IRequestRecord) {
    this.store.dispatch(new EditRequest(oldRequest, modifiedRequest));
  }

  public editOrAddRequestInStore(newOrModifiedRequest: IRequestRecord) {
    let existingRequest: IRequestRecord;

    this.store.select(state => state.requests).take(1).subscribe((requests: IRequestList) => {
      requests.filter((request: IRequestRecord) => request.id == newOrModifiedRequest.id)
        .map((request: IRequestRecord) => {
          existingRequest = request;
        });
    });
    typeof existingRequest == 'undefined' ? this.addRequestInStore(newOrModifiedRequest) : this.updateRequestBalance(existingRequest);
  }

  private updateRequestWithNewFundInfoFromContract(request: IRequestRecord): void {
    this._cs.getRequestFundInfo(request).then((fundInfo) => {
        this.updateRequestWithNewFundInfo(request, fundInfo);
      }
    ).catch(error => {
      console.log(error);
    });
  }

  private updateRequestBalance(request: IRequestRecord): void {
    this._cs.getRequestFundInfo(request).then((fundInfo) => {
      this.updateRequestWithNewFundInfo(request, fundInfo);
    }).catch(error => {
      console.log(error);
    });
  }

  private updateRequestWithNewFundInfo(request: IRequestRecord, fundInfo: RequestIssueFundInformation) {
    let newRequest: IRequestRecord;

    newRequest = !request.set ? createRequest(request) : request;
    newRequest = newRequest.set('fundInfo', fundInfo);
    this.editRequestInStore(request, newRequest);
  }

  private handleError(error: any): void {
    console.error('An error occurred', error);
  }

  public ngOnDestroy() {
    this._subscription.unsubscribe();
  }
}
